package com.github.yuki_0710;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import java.lang.reflect.Method;
import java.net.URI;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.internal.credentials.DefaultAwsCredentials;

/**
 * Gradle plugin for Maven Repository built in S3.
 *
 * @author yuki-0710
 */
public class MavenS3Plugin implements Plugin<Project> {

    private Logger logger;

    /**
     * {@inheritDoc
     */
    @Override
    public void apply(Project project) {

        logger = project.getLogger();

        project.getAllprojects().forEach(this::applyToProject);
    }


    private void applyToProject(Project project) {

        project.afterEvaluate(target -> {

            DefaultAwsCredentials credentials = getCredentials(target);

            if (logger.isInfoEnabled()) {
                logger.info(project.getName() + " uses " + credentials.getAccessKey());
            }

            // Apply to repositories
            target.getRepositories().all(repository -> configureCredentials(repository, credentials));

            // Apply to publishing
            PublishingExtension publishingExtension = target.getExtensions().findByType(PublishingExtension.class);
            if (publishingExtension != null) {
                publishingExtension.getRepositories().all(repository -> configureCredentials(repository, credentials));
            }
        });
    }

    private void configureCredentials(ArtifactRepository repository, DefaultAwsCredentials credentials) {

        // Ignore except Maven repository
        if (!(repository instanceof DefaultMavenArtifactRepository)) {
            return;
        }

        DefaultMavenArtifactRepository mavenRepository = (DefaultMavenArtifactRepository) repository;

        // Ignore except the S3 protocol
        URI url = mavenRepository.getUrl();
        if (url == null || url.getScheme() == null || !url.getScheme().equals("s3")) {
            return;
        }

        // Ignore configured Maven repository
        if (credentialsExists(mavenRepository)) {
            return;
        }

        mavenRepository.setConfiguredCredentials(credentials);

        if (logger.isInfoEnabled()) {
            logger.info("Configured the credentials to " + mavenRepository.getUrl());
        }
    }

    private DefaultAwsCredentials getCredentials(Project project) {

        AWSCredentials credentials = new MavenS3AWSCredentialsProviderChain().getCredentials();
        DefaultAwsCredentials gradleCredentials = new DefaultAwsCredentials();
        gradleCredentials.setAccessKey(credentials.getAWSAccessKeyId());
        gradleCredentials.setSecretKey(credentials.getAWSSecretKey());

        if (credentials instanceof AWSSessionCredentials) {
            AWSSessionCredentials sessionCredentials = (AWSSessionCredentials) credentials;
            gradleCredentials.setSessionToken(sessionCredentials.getSessionToken());
        }

        return gradleCredentials;
    }

    // This is a shim to work around an incompatible API change in Gradle 6.6. Prior to that,
    // AbstractAuthenticationSupportedRepository#getConfiguredCredentials() returned a (possibly null)
    // Credentials object. In 6.6, it changed to return Property<Credentials>.
    //
    // Compiling this plugin against Gradle 6.5 results in a NoSuchMethodException if you run it under
    // Gradle 6.6. The same thing happens if you compile against 6.6 and run it in 6.5.
    //
    // So we have to use reflection to inspect the return type.
    //
    // original code: https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools/pull/37/files#diff-3b482c6e06ded4ee89fb6d111b49b2452ab4625978eb29a93063deee1cab8f52R164
    private boolean credentialsExists(final DefaultMavenArtifactRepository repo) {
        try {
            final Method getConfiguredCredentials = DefaultMavenArtifactRepository.class
                .getMethod("getConfiguredCredentials");

            if (getConfiguredCredentials.getReturnType().equals(Credentials.class)) {
                // This is for Gradle < 6.6. Once we no longer support versions of Gradle before 6.6
                final Credentials existingCredentials =
                    (Credentials) getConfiguredCredentials.invoke(repo);
                return existingCredentials != null;
            }

            if (getConfiguredCredentials.getReturnType().equals(Property.class)) {
                // for Gradle >= 6.6
                final Property<?> existingCredentials =
                    (Property<?>) getConfiguredCredentials.invoke(repo);
                return existingCredentials.isPresent();
            }

            logger.warn("Error determining Gradle credentials API; expect authentication errors");
            return false;
        } catch (ReflectiveOperationException e) {
			logger.warn(
				"Error determining Gradle credentials API; expect authentication errors", e);
            return false;
        }
    }
}
