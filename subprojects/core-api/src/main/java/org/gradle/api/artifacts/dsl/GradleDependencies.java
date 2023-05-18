/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.artifacts.dsl;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Dependency;

/**
 * Dependency APIs available for {@code dependencies} blocks that can build software that relies on Gradle APIs.
 *
 * @apiNote This interface is intended to be used to mix-in methods that expose Gradle-specific dependencies to the DSL.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @since 7.6
 */
@Incubating
public interface GradleDependencies extends Dependencies {
    /**
     * Creates a dependency on the API of the current version of Gradle.
     *
     * @return The dependency.
     * @since 7.6
     */
    default Dependency gradleApi() {
        return getDependencyFactory().gradleApi();
    }

    /**
     * Creates a dependency on the <a href="https://docs.gradle.org/current/userguide/test_kit.html" target="_top">Gradle test-kit</a> API.
     *
     * @return The dependency.
     * @since 7.6
     */
    default Dependency gradleTestKit() {
        return getDependencyFactory().gradleTestKit();
    }

    /**
     * Creates a dependency on the version of Groovy that is distributed with the current version of Gradle.
     *
     * @return The dependency.
     * @since 7.6
     */
    default Dependency localGroovy() {
        return getDependencyFactory().localGroovy();
    }
}
