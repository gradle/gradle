/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.ivy;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;

/**
 * A Collection of {@link IvyArtifact}s to be included in an {@link IvyPublication}.
 *
 * Being a {@link DomainObjectSet}, a {@code IvyArtifactSet} provides convenient methods for querying, filtering, and applying actions to the set of {@link IvyArtifact}s.
 *
 * <pre autoTested="true">
 * apply plugin: 'ivy-publish'
 *
 * def publication = publishing.publications.create("my-pub", IvyPublication)
 * def artifacts = publication.artifacts
 *
 * artifacts.matching({
 *     it.type == "source"
 * }).all({
 *     it.extension = "src.jar"
 * })
 * </pre>
 *
 * @see DomainObjectSet
 */
@Incubating
public interface IvyArtifactSet extends DomainObjectSet<IvyArtifact> {
    /**
     * Creates and adds a {@link IvyArtifact} to the set.
     *
     * The semantics of this method are the same as {@link IvyPublication#artifact(Object)}.
     *
     * @param source The source of the artifact content.
     */
    IvyArtifact artifact(Object source);

    /**
     * Creates and adds a {@link IvyArtifact} to the set, which is configured by the associated action.
     *
     * The semantics of this method are the same as {@link IvyPublication#artifact(Object, Action)}.
     *
     * @param source The source of the artifact.
     * @param config An action to configure the values of the constructed {@link IvyArtifact}.
     */
     IvyArtifact artifact(Object source, Action<? super IvyArtifact> config);
}
