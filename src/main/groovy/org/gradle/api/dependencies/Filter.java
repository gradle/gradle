package org.gradle.api.dependencies;

public enum Filter {
    LIBS_ONLY {public boolean includeDependency(Dependency dependency) {return !PROJECTS_ONLY.includeDependency(dependency);}},
    PROJECTS_ONLY {public boolean includeDependency(Dependency dependency) {return dependency instanceof ProjectDependency;}},
    NONE {public boolean includeDependency(Dependency dependency) {return true;}};

    public abstract boolean includeDependency(Dependency dependency);
}
