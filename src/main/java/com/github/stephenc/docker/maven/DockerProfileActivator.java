package com.github.stephenc.docker.maven;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.mandas.docker.client.DefaultDockerClient;
import org.mandas.docker.client.exceptions.DockerCertificateException;
import org.mandas.docker.client.exceptions.DockerException;

@Component(role = ProfileActivator.class)
public class DockerProfileActivator implements ProfileActivator {
    private static final String PROPERTY_NAME = "[docker:available]";
    private static Boolean dockerPresent;
    /**
     * The names of profiles that we have logged about activation.
     */
    private final ConcurrentMap<String, Boolean> logged = new ConcurrentHashMap<>();
    /**
     * Logger provided by Maven runtime.
     */
    @Requirement
    protected Logger logger;

    private static synchronized boolean isDockerPresent() {
        if (dockerPresent == null) {
            final java.util.logging.Logger noizyLogger =
                    java.util.logging.Logger.getLogger("org.apache.http.impl.execchain.RetryExec");
            final Level oldLevel = noizyLogger.getLevel();
            try {
                noizyLogger.setLevel(Level.WARNING);
                DefaultDockerClient.fromEnv().build().ping();
                dockerPresent = true;
            } catch (DockerException | DockerCertificateException e) {
                dockerPresent = false;
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            } finally {
                noizyLogger.setLevel(oldLevel);
            }
        }
        return dockerPresent;
    }

    @Override
    public boolean isActive(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        if (!presentInConfig(profile, context, problems)) {
            return false;
        }
        final String value = profile.getActivation().getProperty().getValue();
        boolean required = value == null || Boolean.parseBoolean(value);
        logger.debug("Profile " + profile.getId() + " is activated based on the availability of Docker");
        Boolean loggedBefore = logged.putIfAbsent(profile.getId(), required);
        if (isDockerPresent()) {
            if (loggedBefore == null || loggedBefore != required) {
                logger.info(required
                        ? "Activating profile " + profile.getId() + " because Docker is available"
                        : "Not activating profile " + profile.getId() + " because Docker is available");
            } else {
                logger.debug(required
                        ? "Activating profile " + profile.getId() + " because Docker is available"
                        : "Not activating profile " + profile.getId() + " because Docker is available");
            }
            return required;
        } else {
            if (loggedBefore == null || loggedBefore != required) {
                logger.info(required
                        ? "Not activating profile " + profile.getId() + " because Docker is not available"
                        : "Activating profile " + profile.getId() + " because Docker is not available");
            } else {
                logger.debug(required
                        ? "Not activating profile " + profile.getId() + " because Docker is not available"
                        : "Activating profile " + profile.getId() + " because Docker is not available");
            }
            return !required;
        }
    }

    @Override
    public boolean presentInConfig(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        if (profile.getActivation() == null) {
            return false;
        }
        if (profile.getActivation().getProperty() == null) {
            return false;
        }
        if (profile.getActivation().getProperty().getName() == null) {
            return false;
        }
        return PROPERTY_NAME.equalsIgnoreCase(profile.getActivation().getProperty().getName().trim());
    }
}
