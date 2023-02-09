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

package org.gradle.jvm.component.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * A {@link SoftwareComponent} which produces variants intended for use within the JVM ecosystem.
 * <p>
 * TODO: There is currently no public interface for this type of component, as the JVM component
 * infrastructure is still under construction. The main blocker for publicizing this component
 * is the lack of multi-target functionality. This interface currently exposes the
 * {@link #getMainJarTask()}, {@link #getSourceSet()}, {@link #getImplementationConfiguration()},
 * etc. methods, which imply that this component only supports a single compilation target. A
 * multi-target component would not expose these methods at the top-level, but would instead
 * encapsulate them within some kind of {@code targets} or {@code pipelines} container.
 * <p>
 * TODO: Before publicizing this component, we also need to consider how component extensibility works.
 * For example, for the java-library plugin we have the additional {@code api} and {@code compileOnlyApi}
 * dependency configurations. Do we expect that plugin to write a new component interface and extend this in
 * order to add the proper getter methods? What about the concrete component class which implements that new
 * interface? Does it extend the default implementation class? Is there a way we can avoid Java inheritance?
 */
public interface JvmSoftwareComponentInternal extends SoftwareComponent {

    /**
     * Configures this component to publish a javadoc jar alongside the primary artifacts. As a result,
     * this method also configures the necessary configurations and tasks required to produce
     * the javadoc artifact.
     */
    void enableJavadocJarVariant();

    /**
     * Configures this component to publish a sources jar alongside the primary artifacts. As a result,
     * this method also configures the necessary configurations and tasks required to produce
     * the sources artifact.
     */
    void enableSourcesJarVariant();

    /**
     * Get the {@link Jar} task which assembles the resources and compilation outputs into
     * a single artifact.
     *
     * @return A provider which supplies the component's {@link Jar} task.
     */
    TaskProvider<Jar> getMainJarTask();

    /**
     * Get the {@link JavaCompile} task which compiles the Java source files into classes.
     *
     * @return A provider which supplies the component's {@link JavaCompile} task.
     */
    TaskProvider<JavaCompile> getMainCompileJavaTask();

    // TODO: This could use a better name. "output" can be confused with a
    // component's "real" outputs, which are its variants.
    /**
     * Get the resources and compilation outputs for this component which are used as
     * inputs for the {@link #getMainJarTask() main jar} task.
     *
     * @return The source set outputs.
     */
    SourceSetOutput getMainOutput();

    /**
     * Get this component's backing source set.
     * <p>
     * {@link SourceSet#getOutput()} and the classpath-returning methods on the returned
     * source set should ideally be avoided in favor of the similarly-named methods on
     * this component. The concept of source sets having a single set of outputs is only
     * relevant for single-target components.
     *
     * @return This component's source set.
     */
    SourceSet getSourceSet();

    /**
     * Gets the dependency configuration for which to declare dependencies internal to the component.
     * Dependencies declared on this configuration are present during compilation and runtime, but are not
     * exposed as part of the component's API variant.
     *
     * @return The {@code implementation} configuration.
     */
    Configuration getImplementationConfiguration();

    /**
     * Gets the dependency configuration for which to declare runtime-only dependencies.
     * Dependencies declared on this configuration are present only during runtime, are not
     * present during compilation, and are not exposed as part of the component's API variant.
     *
     * @return The {@code runtimeOnly} configuration.
     */
    Configuration getRuntimeOnlyConfiguration();

    /**
     * Gets the dependency configuration for which to declare compile-only dependencies.
     * Dependencies declared on this configuration are present only during compilation, are not
     * present during runtime, and are not exposed as part of the component's API variant.
     *
     * @return The {@code compileOnly} configuration.
     */
    Configuration getCompileOnlyConfiguration();

    /**
     * Get the resolvable configuration containing the resolved runtime dependencies
     * for this component. This configuration does not contain the artifacts from the
     * component's compilation itself.
     *
     * @return The {@code runtimeClasspath} configuration.
     */
    Configuration getRuntimeClasspathConfiguration();

    /**
     * Get the resolvable configuration containing the resolved compile dependencies
     * for this component.
     *
     * @return The {@code compileClasspath} configuration.
     */
    Configuration getCompileClasspathConfiguration();

    /**
     * Get the consumable configuration which produces the {@code runtimeElements} variant of this component.
     * This configuration includes all runtime dependencies as well as the component's
     * compilation outputs, but does not include {@code compileOnly} dependencies.
     *
     * @return The {@code runtimeElements} configuration.
     */
    Configuration getRuntimeElementsConfiguration();

    /**
     * Get the consumable configuration which produces the {@code apiElements} variant of this component.
     * This configuration includes all API compilation dependencies as well as the component's
     * compilation outputs, but does not include {@code implementation}, {@code compileOnly},
     * or {@code runtimeOnly} dependencies.
     *
     * @return The {@code apiElements} configuration.
     */
    Configuration getApiElementsConfiguration();

}
