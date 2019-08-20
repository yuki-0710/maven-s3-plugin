package com.github.yuki_0710;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.internal.AwsProfileNameLoader;

/**
 * An implementation of AWSCredentialsProvider for this plugin.
 * <p>
 * This class defines the priority of AWSCredentialsProvider referenced by the plugin.
 * The highest priority is ProfileCredentialsProvider (default is specified),
 * followed by EnvironmentVariableCredentialsProvider, SystemPropertiesCredentialsProvider, 
 * EC2ContainerCredentialsProviderWrapper.
 * </p>
 * 
 * @see ProfileCredentialsProvider
 * @see EnvironmentVariableCredentialsProvider
 * @see SystemPropertiesCredentialsProvider
 * @see EC2ContainerCredentialsProviderWrapper
 * 
 * @author yuki-0710
 */
public class MavenS3AWSCredentialsProviderChain extends AWSCredentialsProviderChain {

    private static final MavenS3AWSCredentialsProviderChain INSTANCE = new MavenS3AWSCredentialsProviderChain();

    public MavenS3AWSCredentialsProviderChain() {
        super(new ProfileCredentialsProvider(AwsProfileNameLoader.DEFAULT_PROFILE_NAME),
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new EC2ContainerCredentialsProviderWrapper());
    }

    public static MavenS3AWSCredentialsProviderChain getInstance() {
        return INSTANCE;
    }
}
