package org.gradle.api.dependencies.specs;

import org.gradle.api.dependencies.Dependency;
import org.gradle.api.dependencies.ProjectDependency;

public enum Type {
    EXTERNAL {public boolean isOf(Dependency dependency) {return !PROJECT.isOf(dependency);}},
    PROJECT {public boolean isOf(Dependency dependency) {return dependency instanceof ProjectDependency;}};

    public abstract boolean isOf(Dependency dependency);
}
