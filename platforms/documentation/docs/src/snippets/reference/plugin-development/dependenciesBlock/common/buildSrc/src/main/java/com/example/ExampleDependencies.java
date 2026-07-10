package com.example;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

// tag::custom-dependencies-type[]
/**
 * Custom dependencies block for the example plugin.
 */
public interface ExampleDependencies extends Dependencies {
// end::custom-dependencies-type[]

// tag::custom-dependencies-scopes[]
    /**
     * Dependency scope called "implementation"
     */
    DependencyCollector getImplementation();
// end::custom-dependencies-scopes[]
}
