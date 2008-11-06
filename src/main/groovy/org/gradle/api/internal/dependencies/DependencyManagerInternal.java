package org.gradle.api.internal.dependencies;

import org.gradle.api.DependencyManager;
import org.apache.ivy.Ivy;

public interface DependencyManagerInternal extends DependencyManager {
    Ivy getIvy();
}
