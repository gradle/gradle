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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

/**
 * This DSL element is used to add dependencies to a jvm library.
 *
 * <ul>
 *     <li><code>api</code> dependencies are used at compilation and runtime.</li>
 *     <li><code>compileOnlyApi</code> dependencies are used only at compilation and are not available at runtime.</li>
 * </ul>
 *
 * @apiNote This interface combines various {@link Dependencies} APIs into a DSL type that can be used to add dependencies for JVM libraries.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @see org.gradle.api.artifacts.dsl.DependencyHandler For more information.
 * @since 9.4.0
 */
@Incubating
public interface JvmLibraryDependencies extends JvmComponentDependencies {
    /**
     * Returns a {@link DependencyCollector} that collects the set of api dependencies.
     * <p>
     * <code>api</code> dependencies are used at compilation and runtime.
     *
     * @return a {@link DependencyCollector} that collects the set of api dependencies
     * @since 9.4.0
     */
    DependencyCollector getApi();

    /**
     * Returns a {@link DependencyCollector} that collects the set of compile-only api dependencies.
     * <p>
     * <code>compileOnlyApi</code> dependencies are used only at compilation and are not available at runtime.
     *
     * @return a {@link DependencyCollector} that collects the set of compile-only api dependencies
     * @since 9.4.0
     */
    DependencyCollector getCompileOnlyApi();
}
