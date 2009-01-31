package org.gradle.api.internal.dependencies;

import org.gradle.api.DependencyManager;
import org.gradle.api.Transformer;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.dependencies.PublishArtifact;
import org.gradle.api.filter.FilterSpec;
import org.gradle.api.internal.dependencies.ivy.IvyHandler;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import groovy.lang.Closure;

public interface DependencyManagerInternal extends DependencyManager {
    Ivy getIvy();

    IvyHandler getIvyHandler();

    ModuleDescriptor createModuleDescriptor(FilterSpec<Configuration> configurationFilter, FilterSpec<Dependency> dependencyFilter,
                                                   FilterSpec<PublishArtifact> artifactFilter);
}
