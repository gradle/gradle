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

/**
 * A Collection of {@link MavenArtifact}s to be included in a {@link MavenPublication}.
 */
public interface MavenArtifactSet extends DomainObjectSet<MavenArtifact> {
    /**
     * Creates and adds a {@link MavenArtifact} to the set.
     *
     * @param source The source of the artifact content.
     */
    MavenArtifact addArtifact(Object source);

    /**
     * Creates and adds a {@link MavenArtifact} to the set, which is configured by the associated action.
     *
     * @param source The source of the artifact.
     * @param config An action to configure the values of the constructed {@link MavenArtifact}.
     */
     MavenArtifact addArtifact(Object source, Action<? super MavenArtifact> config);
}
