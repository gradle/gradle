package org.gradle.api.artifacts.specs;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ExternalDependency;

public enum Type {
    EXTERNAL {public boolean isOf(Dependency dependency) {return dependency instanceof ExternalDependency;}},
    PROJECT {public boolean isOf(Dependency dependency) {return dependency instanceof ProjectDependency;}};

    public abstract boolean isOf(Dependency dependency);
}
