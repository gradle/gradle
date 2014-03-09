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

package org.gradle.plugins.ide.internal.resolver;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;

import java.util.List;

public interface IdeDependencyResolver {
    /**
     * Gets IDE project dependencies for a given configuration.
     *
     * @param configuration Configuration
     * @param project Project
     * @return List of resolved IDE project dependencies
     */
    List<IdeProjectDependency> getIdeProjectDependencies(Configuration configuration, Project project);

    /**
     * Gets unresolved IDE repository file dependencies for a given configuration.
     *
     * @param configuration Configuration
     * @return List of unresolved IDE repository file dependencies
     */
    List<UnresolvedIdeRepoFileDependency> getUnresolvedIdeRepoFileDependencies(Configuration configuration);

    /**
     * Gets IDE repository file dependencies for a given configuration.
     *
     * @param configuration Configuration
     * @return List of IDE repository file dependencies
     */
    List<IdeExtendedRepoFileDependency> getIdeRepoFileDependencies(Configuration configuration);

    /**
     * Gets IDE local file dependencies for a given configuration.
     *
     * @param configuration Configuration
     * @return List of IDE local file dependencies
     */
    List<IdeLocalFileDependency> getIdeLocalFileDependencies(Configuration configuration);

}
