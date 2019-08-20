package com.github.yuki_0710;

import java.net.URI;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.internal.credentials.DefaultAwsCredentials;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;

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
        if (mavenRepository.getConfiguredCredentials() != null) {
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
}
