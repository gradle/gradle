/*
 * Copyright 2011 the original author or authors.
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

import java.util.Set;

/**
 * Resolved configuration that does not fail eagerly when some dependencies are not resolved, or some artifacts do not exist.
 * <p>
 * This is a legacy API. <strong>Avoid this class for new code</strong>. Lenient artifacts can be acquired
 * through a {@link ArtifactView.ViewConfiguration#lenient(boolean) lenient ArtifactView}. This API will be
 * deprecated and removed in future Gradle versions.
 * <ul>
 *     <li>This class is not configuration-cache compatible.</li>
 *     <li>Returned file sets do not track task dependencies.</li>
 *     <li>The returned types do not reflect the variant-aware nature of the dependency resolution engine.</li>
 * </ul>
 */
public interface LenientConfiguration {

    /**
     * Returns successfully resolved direct dependencies.
     * <p>
     * Prefer {@link org.gradle.api.artifacts.result.ResolutionResult} for traversing the resolved graph or
     * {@link ArtifactView#getArtifacts()} for accessing the resolved artifacts.
     *
     * @return only resolved dependencies
     * @since 3.3
     */
    Set<ResolvedDependency> getFirstLevelModuleDependencies();

    /**
     * Returns all successfully resolved dependencies including transitive dependencies.
     * <p>
     * Prefer {@link org.gradle.api.artifacts.result.ResolutionResult} for traversing the resolved graph or
     * {@link ArtifactView#getArtifacts()} for accessing the resolved artifacts.
     *
     * @since 3.1
     * @return all resolved dependencies
     */
    Set<ResolvedDependency> getAllModuleDependencies();

    /**
     * returns dependencies that were attempted to resolve but failed.
     * If empty then all dependencies are neatly resolved.
     * <p>
     * Prefer {@link org.gradle.api.artifacts.result.ResolutionResult}.
     *
     * @return only unresolved dependencies
     */
    Set<UnresolvedDependency> getUnresolvedModuleDependencies();

    /**
     * Gets successfully resolved artifacts. Ignores dependencies or files that cannot be resolved.
     * <p>
     * Prefer {@link ArtifactView#getArtifacts()}.
     *
     * @return successfully resolved artifacts
     * @since 3.3
     */
    Set<ResolvedArtifact> getArtifacts();

}
