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

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyAdder;
import org.gradle.api.artifacts.dsl.GradleDependencies;
import org.gradle.testing.base.TestSuite;

/**
 * This DSL element is used to add dependencies to a component, for instance a {@link TestSuite}
 *
 * <ul>
 *     <li><code>implementation</code> dependencies are used at compilation and runtime.</li>
 *     <li><code>compileOnly</code> dependencies are used only at compilation and are not available at runtime.</li>
 *     <li><code>runtimeOnly</code> dependencies are not available at compilation and are used only at runtime.</li>
 *     <li><code>annotationProcessor</code> dependencies are used only at compilation for the annotation processor classpath</li>
 * </ul>
 *
 * @apiNote This interface combines various {@link Dependencies} APIs into a DSL type that can be used to add dependencies for JVM components.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @see org.gradle.api.artifacts.dsl.DependencyHandler For more information.
 * @since 7.3
 */
@Incubating
public interface JvmComponentDependencies extends PlatformDependencyModifiers, TestFixturesDependencyModifiers, GradleDependencies {
    /**
     * Returns a {@link DependencyAdder} to add to the set of implementation dependencies.
     * <p>
     * <code>implementation</code> dependencies are used at compilation and runtime.
     *
     * @return a {@link DependencyAdder} to add to the set of implementation dependencies
     * @since 7.6
     */
    DependencyAdder getImplementation();

    /**
     * Returns a {@link DependencyAdder} to add to the set of compile-only dependencies.
     * <p>
     * <code>compileOnly</code> dependencies are used only at compilation and are not available at runtime.
     *
     * @return a {@link DependencyAdder} to add to the set of compile-only dependencies
     * @since 7.6
     */
    DependencyAdder getCompileOnly();

    /**
     * Returns a {@link DependencyAdder} to add to the set of runtime-only dependencies.
     * <p>
     * <code>runtimeOnly</code> dependencies are not available at compilation and are used only at runtime.
     *
     * @return a {@link DependencyAdder} to add to the set of runtime-only dependencies
     * @since 7.6
     */
    DependencyAdder getRuntimeOnly();

    /**
     * Returns a {@link DependencyAdder} to add to the set of annotation processor dependencies.
     * <p>
     * <code>annotationProcessor</code> dependencies are used only at compilation, and are added to the annotation processor classpath.
     *
     * @return a {@link DependencyAdder} to add to the set of annotation processor dependencies
     * @since 7.6
     */
    DependencyAdder getAnnotationProcessor();
}
