/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.jvm.component;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.ComponentFeature;
import org.gradle.api.component.ConsumableVariant;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nullable;

/**
 * A {@link ComponentFeature} which wraps a single {@link SourceSet} and exposes variants for a single
 * JVM compilation. Given that all variants are derived from the same sources, they all expose the
 * same API or "content" and thus provide the same capabilities.
 *
 * <p>This feature transitively owns all tasks and configurations created on behalf of the owned source set,
 * and provides convenience methods to access these objects without needing to query the global containers.</p>
 *
 * <p>This feature is configurable in that it can optionally expose additional variants for javadoc and sources jars.
 * Furthermore, this feature by default does not expose an API. However, it can be configured to do so in order to
 * model constructs like a standard JVM library.</p>
 *
 * @since 8.2
 */
@Incubating
public interface JvmFeature extends ComponentFeature {

    // TODO: Perhaps we should call this withDocumentationJar, or move it to a sub-interface which
    // is java-specific.

    /**
     * Configures this feature to publish a javadoc jar alongside the primary artifacts. As a result,
     * this method also configures the necessary configurations and tasks required to produce
     * the javadoc artifact.
     */
    void withJavadocJar();

    /**
     * Configures this feature to publish a sources jar alongside the primary artifacts. As a result,
     * this method also configures the necessary configurations and tasks required to produce
     * the sources artifact.
     */
    void withSourcesJar();

    // TODO: Future work will need to separate this single JvmFeature into a JvmLibraryFeature
    // and a JvmApplicationFeature, as the latter will not have an API.

    /**
     * Adds the {@code api} and {@code compileOnlyApi} dependency configurations to this feature.
     */
    void withApi();

    /**
     * Gets the consumable configuration created by {@link #withJavadocJar()}.
     *
     * @return null if {@link #withJavadocJar()} has not been called.
     */
    @Nullable
    Configuration getJavadocElementsConfiguration();

    /**
     * Gets the consumable configuration created by {@link #withSourcesJar()}.
     *
     * @return null if {@link #withSourcesJar()} has not been called.
     */
    @Nullable
    Configuration getSourcesElementsConfiguration();

    /**
     * Get the {@link Jar} task which assembles the resources and compilation outputs into
     * a single artifact.
     *
     * @return A provider which supplies the feature's {@link Jar} task.
     */
    TaskProvider<Jar> getJarTask();

    /**
     * Get the {@link JavaCompile} task which compiles the Java source files into classes.
     *
     * @return A provider which supplies the feature's {@link JavaCompile} task.
     */
    TaskProvider<JavaCompile> getCompileJavaTask();

    /**
     * Get this feature's backing source set.
     * <p>
     * {@link SourceSet#getOutput()} and the classpath-returning methods on the returned
     * source set should ideally be avoided in favor of the similarly-named methods on
     * this feature. The concept of source sets having a single set of outputs is only
     * relevant for single-target features.
     *
     * @return This feature's source set.
     */
    SourceSet getSourceSet();

    /**
     * Gets the dependency configuration for which to declare dependencies internal to the feature.
     * Dependencies declared on this configuration are present during compilation and runtime, but are not
     * exposed as part of the feature's API variant.
     *
     * @return The {@code implementation} configuration.
     */
    Configuration getImplementationConfiguration();

    /**
     * Gets the dependency configuration for which to declare runtime-only dependencies.
     * Dependencies declared on this configuration are present only during runtime, are not
     * present during compilation, and are not exposed as part of the feature's API variant.
     *
     * @return The {@code runtimeOnly} configuration.
     */
    Configuration getRuntimeOnlyConfiguration();

    /**
     * Gets the dependency configuration for which to declare compile-only dependencies.
     * Dependencies declared on this configuration are present only during compilation, are not
     * present during runtime, and are not exposed as part of the feature's API variant.
     *
     * @return The {@code compileOnly} configuration.
     */
    Configuration getCompileOnlyConfiguration();

    /**
     * Gets the dependency configuration for which to declare API dependencies.
     * Dependencies declared on this configuration are present during compilation
     * and runtime, and are exposed as part of the feature's API variant.
     *
     * @return null if {@link #withApi()} has not been called.
     */
    @Nullable
    Configuration getApiConfiguration();

    /**
     * Gets the dependency configuration for which to declare compile-only API dependencies.
     * Dependencies declared on this configuration are present during compilation
     * but not runtime, and are exposed as part of the feature's API variant.
     *
     * @return null if {@link #withApi()} has not been called.
     */
    @Nullable
    Configuration getCompileOnlyApiConfiguration();

    /**
     * Get the resolvable configuration containing the resolved runtime dependencies
     * for this feature. This configuration does not contain the artifacts from the
     * feature's compilation itself.
     *
     * @return The {@code runtimeClasspath} configuration.
     */
    Configuration getRuntimeClasspathConfiguration();

    /**
     * Get the resolvable configuration containing the resolved compile dependencies
     * for this feature.
     *
     * @return The {@code compileClasspath} configuration.
     */
    Configuration getCompileClasspathConfiguration();

    /**
     * Get the consumable configuration which produces the {@code apiElements} variant of this feature.
     * This configuration includes all API compilation dependencies as well as the feature's
     * compilation outputs, but does not include {@code implementation}, {@code compileOnly},
     * or {@code runtimeOnly} dependencies.
     *
     * @return The {@code apiElements} configuration.
     */
    Configuration getApiElementsConfiguration();

    /**
     * Get the consumable configuration which produces the {@code runtimeElements} variant of this feature.
     * This configuration includes all runtime dependencies as well as the feature's
     * compilation outputs, but does not include {@code compileOnly} dependencies.
     *
     * @return The {@code runtimeElements} configuration.
     */
    Configuration getRuntimeElementsConfiguration();

    /**
     * {@inheritDoc}
     */
    @Override
    ExtensiblePolymorphicDomainObjectContainer<ConsumableVariant> getVariants();
}
