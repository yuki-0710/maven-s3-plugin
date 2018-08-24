package com.github.yuki_0710;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.credentials.DefaultAwsCredentials;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

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

		AWSCredentials credentials = getSecurePropertiesCredentials(project);
		if (credentials != null) {
			return createGradleCredentials(credentials);
		}

		return createGradleCredentials(new DefaultAWSCredentialsProviderChain().getCredentials());
	}

	private AWSCredentials getSecurePropertiesCredentials(Project project) {

		JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention.class);
		SourceSet testSourceSet = convention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
		SourceDirectorySet resources = testSourceSet.getResources();

		File secureProperties = null;
		for (File file : resources.getFiles()) {
			if (file.isFile() && file.getName().equals("secure.properties")) {
				secureProperties = file;
				break;
			}
		}

		if (secureProperties == null) {
			return null;
		}

		AWSCredentials credentials = null;
		try {
			Properties properties = new Properties();
			properties.load(new FileInputStream(secureProperties));
			String accessKey = properties.getProperty("aws.accessKey");
			String secretKey = properties.getProperty("aws.secretKey");
			credentials = new BasicAWSCredentials(accessKey, secretKey);
		} catch (Exception e) {
			// NOP
		}

		return credentials;
	}

	private DefaultAwsCredentials createGradleCredentials(AWSCredentials credentials) {

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
