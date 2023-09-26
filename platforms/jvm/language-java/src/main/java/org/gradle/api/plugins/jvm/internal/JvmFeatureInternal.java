/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nullable;

/**
 * A Jvm Feature wraps a source set to encapsulate the logic and domain objects required to
 * implement a feature of a JVM component. Features are used to model constructs like
 * production libraries, test suites, test fixtures, applications, etc. While features are not
 * individually consumable themselves for publication or through dependency-management,
 * they can be exposed to consumers via an owning component.
 *
 * <p>Features are classified by their capabilities. Each variant of a feature provides at least
 * the same set of capabilities as the feature itself. Since all variants of a feature are derived
 * from the same sources, they all expose the same API or "content" and thus provide the same
 * capabilities. Some variants may expose additional capabilities than those of its owning feature,
 * for example with fat jars.</p>
 *
 * <p>TODO: The current API is written as if this were a single-target feature. Before we make this API
 * public, we should make this API multi-target aware. Alternatively, we could implement a
 * SingleTargetJvmFeature now and in the future implement a MultiTargetJvmFeature when we're ready.
 * This would allow us to use the JvmFeature interface as a common parent interface.</p>
 */
public interface JvmFeatureInternal {

    /**
     * Get the capabilities of this feature. All variants exposed by this feature must provide at least
     * the same capabilities as this feature.
     */
    CapabilitiesMetadata getCapabilities();

    // TODO: Are sources and javadoc also target-specific? Some targets, for example java 8 vs 11, may use
    // separate sources which compile to version-specific APIs. Same for javadoc. They are built from the sources
    // used for compilation. Also each version of java comes with a separate javadoc tool.

    // TODO: Should Javadoc even live on a generic JVM target? May be can call it withDocumentationJar?
    // Scala and groovy have Groovydoc and Scaladoc. Kotlin has KDoc.

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

    /**
     * Adds the {@code api} and {@code compileOnlyApi} dependency configurations to this feature.
     *
     * TODO: Should this live on the "base" JVM feature? Should all JVM features know how to add
     * an API? Or should we have subclasses which have APIs and others, which support
     * application features and test suites, which do not have APIs?
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

    // TODO: Many of the methods below probably belong on a JvmTarget. Features may have many targets
    // and thus many configurations, jar tasks, compile tasks, etc.

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

}
