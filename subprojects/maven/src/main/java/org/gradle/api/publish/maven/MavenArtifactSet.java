/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.maven;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;

/**
 * A Collection of {@link MavenArtifact}s to be included in a {@link MavenPublication}.
 *
 * Being a {@link DomainObjectSet}, a {@code MavenArtifactSet} provides convenient methods for querying, filtering, and applying actions to the set of {@link MavenArtifact}s.
 *
 * <pre class='autoTested'>
 * apply plugin: 'maven-publish'
 *
 * def publication = publishing.publications.create("name", MavenPublication)
 * def artifacts = publication.artifacts
 *
 * artifacts.matching({
 *     it.classifier == "classy"
 * }).all({
 *     it.extension = "ext"
 * })
 * </pre>
 *
 * @see DomainObjectSet
 */
@Incubating
public interface MavenArtifactSet extends DomainObjectSet<MavenArtifact> {
    /**
     * Creates and adds a {@link MavenArtifact} to the set.
     *
     * The semantics of this method are the same as {@link MavenPublication#artifact(Object)}.
     *
     * @param source The source of the artifact content.
     */
    MavenArtifact artifact(Object source);

    /**
     * Creates and adds a {@link MavenArtifact} to the set, which is configured by the associated action.
     *
     * The semantics of this method are the same as {@link MavenPublication#artifact(Object, Action)}.
     *
     * @param source The source of the artifact.
     * @param config An action or closure to configure the values of the constructed {@link MavenArtifact}.
     */
     MavenArtifact artifact(Object source, Action<? super MavenArtifact> config);
}
