package com.example;

import org.gradle.api.Action;
import org.gradle.api.tasks.Nested;

/**
 * Extension or domain object that can have dependencies.
 */
public interface ExampleExtension {
    // tag::dependencies-accessors[]
    /**
     * Custom dependencies for this extension.
     */
    @Nested
    ExampleDependencies getDependencies();

    /**
     * Configurable block
     */
    default void dependencies(Action<? super ExampleDependencies> action) {
        action.execute(getDependencies());
    }
    // end::dependencies-accessors[]
}
