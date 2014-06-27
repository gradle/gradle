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

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.component.Artifact;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.component.Component;

/**
 * A builder to construct a query that can resolve selected software artifacts of the specified components.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * task resolveCompileSources << {
 *     def componentIds = configurations.compile.incoming.resolutionResult.allDependencies.collect { it.selected.id }
 *
 *     def result = dependencies.createArtifactResolutionQuery()
 *                              .forComponents(componentIds)
 *                              .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
 *                              .execute()
 *
 *     for (component in result.resolvedComponents) {
 *         component.getArtifacts(SourcesArtifact).each { println "Source artifact for ${component.id}: ${it.file}" }
 *     }
 * }
 * </pre>
 *
 * @since 2.0
 */
@Incubating
public interface ArtifactResolutionQuery {
    /**
     * Specifies the set of components to include in the result.
     * @param componentIds The identifiers of the components to be queried.
     */
    ArtifactResolutionQuery forComponents(Iterable<? extends ComponentIdentifier> componentIds);

    /**
     * Specifies the set of components to include in the result.
     * @param componentIds The identifiers of the components to be queried.
     */
    ArtifactResolutionQuery forComponents(ComponentIdentifier... componentIds);

    /**
     * Defines the type of component that is expected in the result, and the artifacts to retrieve for components of this type.
     *
     * Presently, only a single component type and set of artifacts is permitted.
     *
     * @param componentType The expected type of the component.
     * @param artifactTypes The artifacts to retrieve for the queried components.
     */
    ArtifactResolutionQuery withArtifacts(Class<? extends Component> componentType, Class<? extends Artifact>... artifactTypes);

    /**
     * Actually execute the query, returning a query result.
     * Note that {@link #withArtifacts(Class, Class[])} must be called before executing the query.
     */
    ArtifactResolutionResult execute();
}
