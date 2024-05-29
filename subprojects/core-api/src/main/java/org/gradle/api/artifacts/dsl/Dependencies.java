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
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.declarative.dsl.model.annotations.Restricted;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Universal APIs that are available for all {@code dependencies} blocks.
 *
 * @apiNote This interface is intended to be used to mix-in DSL methods for {@code dependencies} blocks.
 * @implSpec The default implementation of all methods should not be overridden.
 * @implNote Changes to this interface may require changes to the
 * {@link org.gradle.api.internal.artifacts.dsl.dependencies.DependenciesExtensionModule extension module for Groovy DSL} or
 * {@link org.gradle.kotlin.dsl.DependenciesExtensions extension functions for Kotlin DSL}.
 * <p>
 * @see <a href="https://docs.gradle.org/current/userguide/custom_gradle_types.html#custom_dependencies_blocks">Creating custom dependencies blocks.</a>
 *
 * @since 7.6
 */
@Incubating
@SuppressWarnings("JavadocReference")
public interface Dependencies {
    /**
     * A dependency factory is used to convert supported dependency notations into {@link org.gradle.api.artifacts.Dependency} instances.
     *
     * @return a dependency factory
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     *
     * @see DependencyFactory
     */
    @Inject
    DependencyFactory getDependencyFactory();

    /**
     * A dependency constraint factory is used to convert supported dependency notations into {@link DependencyConstraint} instances.
     *
     * @return a dependency constraint factory
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     *
     * @see DependencyConstraintFactory
     */
    @Inject
    DependencyConstraintFactory getDependencyConstraintFactory();

    /**
     * The current project. You need to use {@link #project()} or {@link #project(String)} to add a {@link ProjectDependency}.
     *
     * @return current project
     *
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     * @since 8.0
     */
    @Inject
    Project getProject();

    /**
     * Converts an absolute or relative path to a project into a {@link ProjectDependency}. Project paths are separated by colons.
     *
     * This method fails if the project cannot be found.
     *
     * @param projectPath an absolute or relative path (from the current project) to a project
     * @return a {@link ProjectDependency} for the given path
     *
     * @see org.gradle.api.Project#project(String)
     */
    @Restricted
    default ProjectDependency project(String projectPath) {
        return getDependencyFactory().create(getProject().project(projectPath));
    }

    /**
     * Returns the current project as a {@link ProjectDependency}.
     *
     * @return the current project as a dependency
     */
    default ProjectDependency project() {
        return getDependencyFactory().create(getProject());
    }

    /**
     * Create an {@link ExternalModuleDependency} from the given notation.
     *
     * @param dependencyNotation dependency to add
     * @return the new dependency
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    default ExternalModuleDependency module(CharSequence dependencyNotation) {
        return getDependencyFactory().create(dependencyNotation);
    }

    /**
     * Create an {@link ExternalModuleDependency} from a series of strings.
     *
     * @param group the group (optional)
     * @param name the name
     * @param version the version (optional)
     * @return the new dependency
     */
    default ExternalModuleDependency module(@Nullable String group, String name, @Nullable String version) {
        return getDependencyFactory().create(group, name, version);
    }

    /**
     * Create a {@link DependencyConstraint} from the given notation.
     *
     * @param dependencyConstraintNotation dependency constraint to add
     * @return the new dependency constraint
     * @see DependencyConstraintFactory#create(CharSequence) Valid dependency constraint notation for this method
     * @since 8.7
     */
    default DependencyConstraint constraint(CharSequence dependencyConstraintNotation) {
        return getDependencyConstraintFactory().create(dependencyConstraintNotation);
    }

    /**
     * Create a {@link DependencyConstraint} from a minimal dependency.
     *
     * @param dependencyConstraint dependency constraint to add
     * @return the new dependency constraint
     * @since 8.7
     */
    default Provider<? extends DependencyConstraint> constraint(Provider<? extends MinimalExternalModuleDependency> dependencyConstraint) {
        return dependencyConstraint.map(getDependencyConstraintFactory()::create);
    }

    /**
     * Create a {@link DependencyConstraint} from a minimal dependency.
     *
     * @param dependencyConstraint dependency constraint to add
     * @return the new dependency constraint
     * @since 8.7
     */
    default Provider<? extends DependencyConstraint> constraint(ProviderConvertible<? extends MinimalExternalModuleDependency> dependencyConstraint) {
        return constraint(dependencyConstraint.asProvider());
    }

    /**
     * Create a {@link DependencyConstraint} from a project.
     *
     * @param project the project
     * @return the new dependency constraint
     * @since 8.7
     */
    default DependencyConstraint constraint(ProjectDependency project) {
        return getDependencyConstraintFactory().create(project);
    }

    /**
     * Injected service to create named objects.
     *
     * @return injected service
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     */
    @Inject
    ObjectFactory getObjectFactory();
}
