package org.gradle.plugin.management.internal;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.plugin.management.PluginManagementSpec;

public interface InternalPluginManagementSpec extends PluginManagementSpec {

    InternalPluginResolutionStrategy getPluginResolutionStrategy();

    void createArtifactRepositories(RepositoryHandler repositoryHandler);

    PluginManagementPluginResolver getResolver();
}
