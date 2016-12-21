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

import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Set;

/**
 * Resolved configuration that does not fail eagerly when some dependencies are not resolved, or some artifacts do not exist.
 */
public interface LenientConfiguration {
    /**
     * Returns successfully resolved direct dependencies.
     *
     * @return only resolved dependencies
     * @since 3.3
     */
    Set<ResolvedDependency> getFirstLevelModuleDependencies();

    /**
     * Returns successfully resolved dependencies that match the given spec.
     *
     * @param dependencySpec dependency spec
     * @return only resolved dependencies
     */
    Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec);

    /**
     * Returns all successfully resolved dependencies including transitive dependencies.
     *
     * @since 3.1
     * @return all resolved dependencies
     */
    Set<ResolvedDependency> getAllModuleDependencies();

    /**
     * returns dependencies that were attempted to resolve but failed.
     * If empty then all dependencies are neatly resolved.
     *
     * @return only unresolved dependencies
     */
    Set<UnresolvedDependency> getUnresolvedModuleDependencies();

    /**
     * Returns successfully resolved files. Ignores dependencies or files that cannot be resolved.
     *
     * @return resolved dependencies files
     * @since 3.3
     */
    Set<File> getFiles();

    /**
     * Returns successfully resolved files. Ignores dependencies or files that cannot be resolved.
     *
     * @param dependencySpec dependency spec
     * @return resolved dependencies files
     */
    Set<File> getFiles(Spec<? super Dependency> dependencySpec);

    /**
     * Gets successfully resolved artifacts. Ignores dependencies or files that cannot be resolved.
     *
     * @return successfully resolved artifacts
     * @since 3.3
     */
    Set<ResolvedArtifact> getArtifacts();

    /**
     * Gets successfully resolved artifacts. Ignores dependencies or files that cannot be resolved.
     *
     * @param dependencySpec dependency spec
     * @return successfully resolved artifacts for dependencies that match given dependency spec
     */
    Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec);
}
