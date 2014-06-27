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

import java.util.Set;

/**
 * The result of executing an artifact resolution query.
 *
 * @since 2.0
 */
@Incubating
public interface ArtifactResolutionResult {
    /**
     * <p>Return a set of ComponentResults representing all requested components.
     *
     * <p>Each element in the returned set is declared as an opaque {@link org.gradle.api.artifacts.result.ComponentResult}.
     *    However each element in the result will also implement one of the following interfaces:</p>
     *
     * <ul>
     *     <li>{@link ComponentArtifactsResult} for any component whose ID could be resolved in the set of repositories.</li>
     *     <li>{@link org.gradle.api.artifacts.result.UnresolvedComponentResult} for any component whose ID could not be resolved from the set of repositories.</li>
     * </ul>
     * @return the set of results for all requested components
     */
    Set<ComponentResult> getComponents();

    /**
     * <p>Return a set of ComponentResults representing all successfully resolved components.
     *
     * <p>Calling this method is the same as calling {@link #getComponents()} and filtering the resulting set for elements of type {@link ComponentArtifactsResult}.
     * @return the set of all successfully resolved components
     */
    Set<ComponentArtifactsResult> getResolvedComponents();
}
