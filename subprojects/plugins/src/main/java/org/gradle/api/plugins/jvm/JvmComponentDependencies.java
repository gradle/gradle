/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Dependency;

/**
 * This DSL element is used to add dependencies to a component, like {@link JvmTestSuite}.
 *
 * <ul>
 *     <li><code>implementation</code> dependencies are used at compilation and runtime.</li>
 *     <li><code>compileOnly</code> dependencies are used only at compilation and are not available at runtime.</li>
 *     <li><code>runtimeOnly</code> dependencies are not available at  compilation and are used only at runtime.</li>
 * </ul>
 *
 * @since 7.3
 *
 * @see org.gradle.api.artifacts.dsl.DependencyHandler For more information.
 */
@Incubating
public interface JvmComponentDependencies {
    /**
     * Add a dependency to the set of implementation dependencies.
     * <p><br>
     * <code>implementation</code> dependencies are used at compilation and runtime.
     *
     * @param dependencyNotation dependency to add
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     */
    void implementation(Object dependencyNotation);
    /**
     * Add a dependency to the set of implementation dependencies with additional configuration.
     * <p><br>
     * <code>implementation</code> dependencies are used at compilation and runtime.
     *
     * @param dependencyNotation dependency to add
     * @param configuration additional configuration for the provided dependency
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     */
    void implementation(Object dependencyNotation, Action<? super Dependency> configuration);

    /**
     * Add a dependency to the set of compileOnly dependencies.
     * <p><br>
     * <code>compileOnly</code> dependencies are used only at compilation and are not available at runtime.
     *
     * @param dependencyNotation dependency to add
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     */
    void compileOnly(Object dependencyNotation);
    /**
     * Add a dependency to the set of compileOnly dependencies with additional configuration.
     * <p><br>
     * <code>compileOnly</code> dependencies are used only at compilation and are not available at runtime.
     *
     * @param dependencyNotation dependency to add
     * @param configuration additional configuration for the provided dependency
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     */
    void compileOnly(Object dependencyNotation, Action<? super Dependency> configuration);

    /**
     * Add a dependency to the set of runtimeOnly dependencies.
     * <p><br>
     * <code>runtimeOnly</code> dependencies are not available at  compilation and are used only at runtime.
     *
     * @param dependencyNotation dependency to add
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     */
    void runtimeOnly(Object dependencyNotation);
    /**
     * Add a dependency to the set of runtimeOnly dependencies with additional configuration.
     * <p><br>
     * <code>runtimeOnly</code> dependencies are not available at  compilation and are used only at runtime.
     *
     * @param dependencyNotation dependency to add
     * @param configuration additional configuration for the provided dependency
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     */
    void runtimeOnly(Object dependencyNotation, Action<? super Dependency> configuration);

    /**
     * Add a dependency to the set of annotationProcessor dependencies.
     * <p><br>
     * <code>annotationProcessor</code> dependencies containing annotation processors to be run at compile time.
     *
     * @param dependencyNotation dependency to add
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     * @since 7.5
     */
    void annotationProcessor(Object dependencyNotation);
    /**
     * Add a dependency to the set of annotationProcessor dependencies.
     * <p><br>
     * <code>annotationProcessor</code> dependencies containing annotation processors to be run at compile time.
     *
     * @param dependencyNotation dependency to add
     * @param configuration additional configuration for the provided dependency
     * @see org.gradle.api.artifacts.dsl.DependencyHandler Valid dependency notations.
     * @since 7.5
     */
    void annotationProcessor(Object dependencyNotation, Action<? super Dependency> configuration);
}
