/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.plugins.jvm.JvmLibraryDependencies;
import org.gradle.api.tasks.Nested;
import org.gradle.declarative.dsl.model.annotations.Configuring;

/**
 * The extension for the JavaTestFixturesPlugin to add dependencies to the test fixtures.
 *
 * @since 9.4.0
 */
@Incubating
public interface JavaTestFixturesExtension {

    /**
     * Dependency handler for this library.
     *
     * @return dependency handler
     *
     * @since 9.4.0
     */
    @Nested
    JvmLibraryDependencies getDependencies();

    /**
     * Configure dependencies for this library.
     *
     * @since 9.4.0
     */
    @Configuring
    default void dependencies(Action<JvmLibraryDependencies> configure) {
        configure.execute(getDependencies());
    }
}
