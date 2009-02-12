package org.gradle.api.artifacts.specs;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;

public enum Type {
    EXTERNAL {public boolean isOf(Dependency dependency) {return !PROJECT.isOf(dependency);}},
    PROJECT {public boolean isOf(Dependency dependency) {return dependency instanceof ProjectDependency;}};

    public abstract boolean isOf(Dependency dependency);
}
