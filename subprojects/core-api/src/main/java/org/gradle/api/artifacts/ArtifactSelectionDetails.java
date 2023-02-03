/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.artifacts;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Details about artifact dependency substitution: this class gives access to the
 * original dependency requested artifacts, if any, and gives the opportunity to
 * replace the original requested artifacts with other artifacts.
 *
 * This can typically be used whenever you need to substitute a dependency with
 * uses a classifier to a non-classified dependency, or the other way around.
 *
 * @since 6.6
 */
public interface ArtifactSelectionDetails {
    /**
     * Returns true if the dependency has requested a special artifact (either classifier, type or extension)
     */
    boolean hasSelectors();

    /**
     * Returns the list of requested artifacts for the dependency
     */
    List<DependencyArtifactSelector> getRequestedSelectors();

    /**
     * Removes all artifact selectors, if any.
     */
    void withoutArtifactSelectors();

    /**
     * Adds an artifact to substitute. The first time this method is called, the original artifacts
     * are replaced with the artifact defined by this method call. If you wish to add artifacts to
     * the original query, you need to call {@link #getRequestedSelectors()} and add them using
     * {@link #selectArtifact(DependencyArtifactSelector)}.
     *
     * @param type the type of the artifact being queried
     * @param extension the extension, defaults to the type
     * @param classifier the classifier, defaults to null (no classifier)
     */
    void selectArtifact(String type, @Nullable String extension, @Nullable String classifier);

    /**
     * Adds an artifact to substitute.
     *
     * This method is a convenience to re-register artifacts requested by the original
     * dependency.
     *
     * In most cases, the appropriate method to call is {@link #selectArtifact(String, String, String)}
     */
    void selectArtifact(DependencyArtifactSelector selector);
}
