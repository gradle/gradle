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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;

import java.util.List;

public interface ConfigurationResolver {
    /**
     * Traverses enough of the graph to calculate the build dependencies of the given configuration. All failures are packaged in the result.
     */
    void resolveBuildDependencies(ConfigurationInternal configuration, ResolverResults result);

    /**
     * Traverses the full dependency graph of the given configuration. All failures are packaged in the result.
     */
    void resolveGraph(ConfigurationInternal configuration, ResolverResults results) throws ResolveException;

    /**
     * Calculates the artifacts to include in the result for the given configuration. All failures are packaged in the result.
     * Must be called using the same result instance as was passed to {@link #resolveGraph(ConfigurationInternal, ResolverResults)}.
     */
    void resolveArtifacts(ConfigurationInternal configuration, ResolverResults results) throws ResolveException;

    /**
     * Returns the list of repositories available to resolve a given configuration. This is used for reporting only.
     */
    List<ResolutionAwareRepository> getRepositories();

}
