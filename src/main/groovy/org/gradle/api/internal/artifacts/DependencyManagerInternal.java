package org.gradle.api.internal.artifacts;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.DependencyManager;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.specs.Spec;

public interface DependencyManagerInternal extends DependencyManager {
    Ivy getIvy();

    IvyService getIvyHandler();

    ModuleDescriptor createModuleDescriptor(Spec<Configuration> configurationSpec, Spec<Dependency> dependencySpec,
                                                   Spec<PublishArtifact> artifactSpec);
}
