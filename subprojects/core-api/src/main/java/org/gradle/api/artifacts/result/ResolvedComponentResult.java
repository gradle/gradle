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

package org.gradle.api.artifacts.result;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Represents a component instance in the resolved dependency graph. Provides some basic identity and dependency information about the component.
 */
@Incubating
// Should really be named ComponentGraphResult
public interface ResolvedComponentResult {

    /**
     * <p>Returns the identifier of this component. This can be used to uniquely identify the component within the current build, but it is not necessarily unique between
     * different builds.
     *
     * <p>The return type is declared as an opaque {@link ComponentIdentifier}, however the identifier may also implement one of the following interfaces:</p>
     *
     * <ul>
     *     <li>{@link org.gradle.api.artifacts.component.ProjectComponentIdentifier} for those component instances which are produced by the current build.</li>
     *     <li>{@link org.gradle.api.artifacts.component.ModuleComponentIdentifier} for those component instances which are found in some repository.</li>
     * </ul>
     *
     * @return the identifier of this component
     */
    ComponentIdentifier getId();

    /**
     * <p>Returns the dependencies of this component. Includes resolved and unresolved dependencies (if any).
     *
     * <p>The elements of the returned collection are declared as {@link DependencyResult}, however the dependency instances will also implement one of the
     * following instances:</p>
     *
     * <ul>
     *     <li>{@link ResolvedDependencyResult} for dependencies which were successfully resolved.</li>
     *     <li>{@link UnresolvedDependencyResult} for dependencies which could not be resolved for some reason.</li>
     * </ul>
     *
     * @return the dependencies of this component
     */
    Set<? extends DependencyResult> getDependencies();

    /**
     * Returns the incoming dependencies of this component.
     *
     * @return the dependents of this component
     */
    Set<? extends ResolvedDependencyResult> getDependents();

    /**
     * Returns the reason why this particular component was selected in the result.
     * Useful if multiple candidate components were found during dependency resolution.
     *
     * @return the reason for selecting the component
     */
    ComponentSelectionReason getSelectionReason();

    /**
     * Returns the module version which this component belongs to, if any. A component will belong to a module version if it was found in some repository, or if the
     * module version for the component has been declared, usually by declaring how the component should be published.
     *
     * @return the module version of the component, or {@code null} if this component has no associated module version.
     */
    @Nullable
    ModuleVersionIdentifier getModuleVersion();
}
