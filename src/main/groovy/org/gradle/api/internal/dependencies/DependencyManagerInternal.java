package org.gradle.api.internal.dependencies;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.DependencyManager;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.dependencies.PublishArtifact;
import org.gradle.api.specs.Spec;

public interface DependencyManagerInternal extends DependencyManager {
    Ivy getIvy();

    IvyService getIvyHandler();

    ModuleDescriptor createModuleDescriptor(Spec<Configuration> configurationSpec, Spec<Dependency> dependencySpec,
                                                   Spec<PublishArtifact> artifactSpec);
}
