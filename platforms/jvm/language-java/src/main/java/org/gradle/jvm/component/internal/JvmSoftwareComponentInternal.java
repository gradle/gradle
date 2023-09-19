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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.plugins.JavaResolutionConsistency;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;

/**
 * A {@link SoftwareComponent} which produces variants intended for use within the JVM ecosystem.
 *
 * <p>TODO: There is currently no public interface for this type of component, as the JVM component
 * infrastructure is still under construction. The main blocker for publicizing this component
 * is the lack of a proper variant API and support for dynamically adding Features to a component.</p>
 *
 * <p>TODO: Before publicizing this component, we also need to consider how component extensibility works.
 * For example, for the java-library plugin we have the additional {@code api} and {@code compileOnlyApi}
 * dependency configurations. Do we expect that plugin to write a new feature interface and extend a base
 * interface in order to add the proper getter methods? What about the concrete feature class which implements
 * that new interface? Does it extend the default implementation class? Is there a way we can avoid
 * Java inheritance?</p>
 *
 * Even though the single implementation of this interface also implements {@link org.gradle.api.component.AdhocComponentWithVariants},
 * we do not want to extend that interface here, as it is slated for removal.
 */
public interface JvmSoftwareComponentInternal extends SoftwareComponent {

    // TODO: These with* names are not ideally named. Traditionally, "withers" create a new
    // instance with the value changed, but these mutate the component. However, other names
    // like like "enableJavadocJar" or "addJavadocJar" are also not great since their names
    // are not declarative.
    /**
     * Configures this component to publish a javadoc jar alongside the primary artifacts. As a result,
     * this method also configures the necessary configurations and tasks required to produce
     * the javadoc artifact.
     */
    void withJavadocJar();

    /**
     * Configures this component to publish a sources jar alongside the primary artifacts. As a result,
     * this method also configures the necessary configurations and tasks required to produce
     * the sources artifact.
     */
    void withSourcesJar();

    // TODO: Future iterations of this component should support dynamically adding new features.

    /**
     * Get the feature which encapsulates all logic and domain objects for building the production software product.
     */
    JvmFeatureInternal getMainFeature();

    /**
     * Sets consistent resolution for this component, this ensures that the runtime classpath of the component
     * uses the same version of dependencies as the compile classpath.
     *
     * This also configures all {@link org.gradle.api.tasks.SourceSet}s in the project to resolve consistently
     * with themselves, aligning their compile and runtime configurations as requested.  This is done in order
     * to support custom {@link org.gradle.api.component.Component}-less source sets.
     *
     * @param action the action to configure the consistency
     * @param project the project containing the component for which to enable consistent resolution
     */
    void consistentResolution(Action<? super JavaResolutionConsistency> action, Project project);
}
