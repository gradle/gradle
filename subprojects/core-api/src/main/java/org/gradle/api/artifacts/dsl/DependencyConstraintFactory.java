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
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

/**
 * Factory class for creating {@link DependencyConstraint} instances, with strong typing.
 *
 * <p>
 * An instance of the factory can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@link javax.inject.Inject}.
 * </p>
 *
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 * </p>
 *
 * @since 8.7
 */
@HasInternalProtocol
@NonExtensible
@Incubating
@ServiceScope(Scopes.Build.class)
public interface DependencyConstraintFactory {
    /**
     * Create a {@link DependencyConstraint} from the <code>"<i>group</i>:<i>name</i>:<i>version</i>"</code> notation.
     *
     * <p>
     * Note that no classifier or extension is accepted here.
     * </p>
     *
     * @param dependencyConstraintNotation the dependency constraint notation
     * @return the new dependency constraint
     * @since 8.7
     */
    DependencyConstraint create(CharSequence dependencyConstraintNotation);

    /**
     * Create a {@link DependencyConstraint} from a series of strings.
     *
     * @param group the group (optional)
     * @param name the name
     * @param version the version (optional)
     * @return the new dependency constraint
     * @since 8.7
     */
    DependencyConstraint create(@Nullable String group, String name, @Nullable String version);

    /**
     * Create a {@link DependencyConstraint} from a {@link MinimalExternalModuleDependency}.
     *
     * @param dependency the dependency
     * @return the new dependency constraint
     * @since 8.7
     */
    DependencyConstraint create(MinimalExternalModuleDependency dependency);

    /**
     * Create a {@link DependencyConstraint} from a {@link ProjectDependency}.
     *
     * @param project the project dependency
     * @return the new dependency constraint
     * @since 8.7
     */
    DependencyConstraint create(ProjectDependency project);
}
