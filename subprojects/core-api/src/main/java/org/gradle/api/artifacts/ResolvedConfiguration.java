/*
 * Copyright 2009 the original author or authors.
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
 * A {@code ResolvedConfiguration} represents the result of resolving a {@link Configuration}, and provides access
 * to both the graph and artifacts of the result.
 * <p>
 * This is a legacy API. <strong>Avoid this class for new code</strong>. Prefer accessing resolution outputs
 * via {@link Configuration#getIncoming()}. This API will be deprecated and removed in future Gradle versions.
 * <ul>
 *     <li>This class is not configuration-cache compatible.</li>
 *     <li>Returned file sets do not track task dependencies.</li>
 *     <li>The returned types do not reflect the variant-aware nature of the dependency resolution engine.</li>
 * </ul>
 */
public interface ResolvedConfiguration {
    /**
     * Returns whether all dependencies were successfully retrieved or not.
     */
    boolean hasError();

    /**
     * Provides configuration that does not fail eagerly when some dependencies are not resolved.
     */
    LenientConfiguration getLenientConfiguration();

    /**
     * A resolve of a configuration that is not successful does not automatically throws an exception.
     * Such a exception is only thrown if the result of a resolve is accessed. You can force the throwing
     * of such an exception by calling this method.
     *
     * <p>This method does nothing when resolution was successful.</p>
     *
     * @throws ResolveException when the resolve was not successful.
     */
    void rethrowFailure() throws ResolveException;

    /**
     * Returns the files for the configuration dependencies.
     *
     * @return The artifact files of the specified dependencies.
     * @throws ResolveException when the resolve was not successful.
     * @since 3.3
     *
     * @deprecated Use {@link ResolvableDependencies#getFiles()} instead.
     */
    @Deprecated
    Set<File> getFiles() throws ResolveException;

    /**
     * Returns the files for the specified subset of configuration dependencies.
     *
     * @param dependencySpec The filter for the configuration dependencies.
     * @return The artifact files of the specified dependencies.
     * @throws ResolveException when the resolve was not successful.
     *
     * @deprecated Use an {@link ArtifactView} with a {@code componentFilter} instead.
     */
    @Deprecated
    Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException;

    /**
     * Returns the {@link ResolvedDependency} instances for each direct dependency of the configuration. Via those
     * you have access to all {@link ResolvedDependency} instances, including the transitive dependencies of the
     * configuration.
     * <p>
     * Prefer {@link org.gradle.api.artifacts.result.ResolutionResult} for traversing the resolved graph or
     * {@link ResolvableDependencies#getArtifacts()} for accessing the resolved artifacts.
     *
     * @return A {@code ResolvedDependency} instance for each direct dependency.
     * @throws ResolveException when the resolve was not successful.
     */
    Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException;

    /**
     * Returns the {@link ResolvedDependency} instances for each direct dependency of the configuration that matches
     * the given spec. Via those you have access to all {@link ResolvedDependency} instances, including the transitive
     * dependencies of the configuration.
     *
     * @param dependencySpec A filter for the dependencies to be resolved.
     * @return A {@code ResolvedDependency} instance for each direct dependency.
     * @throws ResolveException when the resolve was not successful.
     *
     * @deprecated Use {@link #getFirstLevelModuleDependencies()}.
     */
    @Deprecated
    Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException;

    /**
     * Returns the set of artifact meta-data for this configuration.
     * <p>
     * Prefer {@link ResolvableDependencies#getArtifacts()}.
     *
     * @return The set of artifacts.
     * @throws ResolveException when the resolve was not successful.
     */
    Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException;
}
