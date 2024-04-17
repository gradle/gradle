package com.example;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

/**
 * Custom dependencies block for the example plugin.
 */
public interface ExampleDependencies extends Dependencies {
    /**
     * Dependency scope called "implementation"
     */
    DependencyCollector getImplementation();
}
