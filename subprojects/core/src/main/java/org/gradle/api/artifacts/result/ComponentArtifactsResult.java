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
package org.gradle.api.artifacts.result;

import org.gradle.api.Incubating;
import org.gradle.api.component.Artifact;

import java.util.Set;

/**
 * The result of successfully resolving a component with a set of artifacts.
 *
 * @since 2.0
 */
@Incubating
public interface ComponentArtifactsResult extends ComponentResult {
    /**
     * <p>Returns the component artifacts of the specified type. Includes resolved and unresolved artifacts (if any).
     *
     * <p>The elements of the returned collection are declared as {@link ArtifactResult}, however the artifact instances will also implement one of the
     * following instances:</p>
     *
     * <ul>
     *     <li>{@link ResolvedArtifactResult} for artifacts which were successfully resolved.</li>
     *     <li>{@link UnresolvedArtifactResult} for artifacts which could not be resolved for some reason.</li>
     * </ul>
     *
     * @return the artifacts of this component with the specified type, or an empty set if no artifacts of the type exist.
     */
    Set<ArtifactResult> getArtifacts(Class<? extends Artifact> type);
}
