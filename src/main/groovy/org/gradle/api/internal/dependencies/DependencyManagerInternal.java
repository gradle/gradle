package org.gradle.api.internal.dependencies;

import org.apache.ivy.Ivy;
import org.gradle.api.DependencyManager;

public interface DependencyManagerInternal extends DependencyManager {
    Ivy getIvy();

    IDependencyResolver getDependencyResolver();
}
