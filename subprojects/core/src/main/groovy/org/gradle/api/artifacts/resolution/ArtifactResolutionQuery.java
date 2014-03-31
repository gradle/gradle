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
package org.gradle.api.artifacts.resolution;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ComponentIdentifier;

/**
 * Resolves selected software artifacts of the given components.
 *
 * @since 1.12
 */
@Incubating
public interface ArtifactResolutionQuery {
    ArtifactResolutionQuery forComponents(Iterable<? extends ComponentIdentifier> componentIds);
    ArtifactResolutionQuery forComponents(ComponentIdentifier... componentIds);
    <T extends SoftwareComponent, U extends SoftwareArtifact> ArtifactResolutionQuery withArtifacts(Class<T> componentType, Class<U>... artifactTypes);
    ArtifactResolutionQueryResult execute();
}
