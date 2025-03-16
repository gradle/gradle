/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.artifacts.query;

import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;

import java.util.Collection;

/**
 * A builder to construct a query that can resolve selected software artifacts of the specified components.
 * <p>
 * This is a legacy API and is in maintenance mode. In future versions of Gradle,
 * this API will be deprecated and removed. New code should not use this API. Prefer
 * {@link ArtifactView.ViewConfiguration#withVariantReselection()} for resolving
 * sources and javadoc.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 * }
 *
 * task resolveCompileSources {
 *     doLast {
 *         def componentIds = configurations.compileClasspath.incoming.resolutionResult.allDependencies.collect { it.selected.id }
 *
 *         def result = dependencies.createArtifactResolutionQuery()
 *                                  .forComponents(componentIds)
 *                                  .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
 *                                  .execute()
 *
 *         for (component in result.resolvedComponents) {
 *             component.getArtifacts(SourcesArtifact).each { println "Source artifact for ${component.id}: ${it.file}" }
 *         }
 *     }
 * }
 * </pre>
 *
 * @since 2.0
 */
public interface ArtifactResolutionQuery {
    /**
     * Specifies the set of components to include in the result.
     *
     * @param componentIds The identifiers of the components to be queried.
     */
    ArtifactResolutionQuery forComponents(Iterable<? extends ComponentIdentifier> componentIds);

    /**
     * Specifies the set of components to include in the result.
     *
     * @param componentIds The identifiers of the components to be queried.
     */
    ArtifactResolutionQuery forComponents(ComponentIdentifier... componentIds);

    /**
     * Specifies a module component to include in the result using its GAV coordinates.
     *
     * @param group Module group.
     * @param name Module name.
     * @param version Module version.
     * @since 4.5
     */
    ArtifactResolutionQuery forModule(String group, String name, String version);

    /**
     * Defines the type of component that is expected in the result, and the artifacts to retrieve for components of this type.
     *
     * Presently, only a single component type and set of artifacts is permitted.
     *
     * @param componentType The expected type of the component.
     * @param artifactTypes The artifacts to retrieve for the queried components.
     */
    @SuppressWarnings("unchecked")
    ArtifactResolutionQuery withArtifacts(Class<? extends Component> componentType, Class<? extends Artifact>... artifactTypes);

    /**
     * Defines the type of component that is expected in the result, and the artifacts to retrieve for components of this type.
     *
     * Presently, only a single component type and set of artifacts is permitted.
     *
     * @param componentType The expected type of the component.
     * @param artifactTypes The artifacts to retrieve for the queried components.
     * @since 4.5
     */
    ArtifactResolutionQuery withArtifacts(Class<? extends Component> componentType, Collection<Class<? extends Artifact>> artifactTypes);

    /**
     * Actually execute the query, returning a query result.
     * Note that {@link #withArtifacts(Class, Class[])} must be called before executing the query.
     */
    ArtifactResolutionResult execute();
}
