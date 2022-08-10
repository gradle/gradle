/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.artifacts.dsl;

import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.internal.HasInternalProtocol;

import java.util.Map;

/**
 * Factory class for creating {@link Dependency} instances, with strong typing.
 *
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 * </p>
 *
 * @since 7.6
 */
@HasInternalProtocol
@NonExtensible
@Incubating
public interface DependencyFactory {
    /**
     * Create an {@link ExternalModuleDependency} from the <code>"<i>group</i>:<i>name</i>:<i>version</i>:<i>classifier</i>@<i>extension</i>"</code> notation.
     *
     * <p>
     * Classifier and extension may each separately be omitted. Version may be omitted if there is no classifier.
     * </p>
     *
     * @param dependencyNotation the dependency notation
     * @return the new dependency
     */
    ExternalModuleDependency createFromCharSequence(CharSequence dependencyNotation);

    /**
     * Lazily create an {@link ExternalModuleDependency}, using the same notation as {@link #createFromCharSequence(CharSequence)}.
     *
     * @param dependencyNotation the dependency notation
     * @return a provider for the new dependency
     */
    default Provider<ExternalModuleDependency> createFromCharSequence(Provider<? extends CharSequence> dependencyNotation) {
        return dependencyNotation.map(this::createFromCharSequence);
    }


    /**
     * Create an {@link ExternalModuleDependency} from a {@link Map}. The map may contain the following keys:
     * <ul>
     *     <li>{@code group}</li>
     *     <li>{@code name}</li>
     *     <li>{@code version}</li>
     *     <li>{@code classifier}</li>
     *     <li>{@code extension}</li>
     * </ul>
     *
     * @param map the dependency map
     * @return the new dependency
     */
    ExternalModuleDependency createFromMap(Map<String, ?> map);

    /**
     * Lazily create an {@link ExternalModuleDependency}, using the same notation as {@link #createFromMap(Map)}.
     *
     * @param map the dependency map
     * @return a provider for the new dependency
     */
    default Provider<ExternalModuleDependency> createFromMap(Provider<? extends Map<String, ?>> map) {
        return map.map(this::createFromMap);
    }

    /**
     * Create a {@link FileCollectionDependency} from a {@link FileCollection}.
     *
     * @param fileCollection the file collection
     * @return the new dependency
     */
    FileCollectionDependency createFromFileCollection(FileCollection fileCollection);

    /**
     * Create a {@link ProjectDependency} from a {@link Project}.
     *
     * @param project the project
     * @return the new dependency
     */
    ProjectDependency createFromProject(Project project);

    /**
     * Lazily create a {@link ProjectDependency}, using the same notation as {@link #createFromProject(Project)}.
     *
     * @param project the project
     */
    default Provider<ProjectDependency> createFromProject(Provider<? extends Project> project) {
        return project.map(this::createFromProject);
    }
}
