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

import java.util.Set;

/**
 * Resolved module version result is a node in the resolved dependency graph.
 * Contains the identifier of the module and the dependencies.
 */
@Incubating
public interface ResolvedModuleVersionResult {

    /**
     * The identifier of the resolved module.
     *
     * @return identifier
     */
    ModuleVersionIdentifier getId();

    /**
     * The dependencies of the resolved module. See {@link ResolvedDependencyResult}
     *
     * @return dependencies
     */
    Set<? extends ResolvedDependencyResult> getDependencies();

    /**
     * The dependents of the resolved module. See {@link ResolvedDependencyResult}.
     *
     * @return dependents
     */
    Set<? extends ResolvedDependencyResult> getDependents();

    /**
     * Informs why this module version was selected.
     * Useful information if during the dependency resolution multiple candidate versions were found
     * and one of them was selected as a part of conflict resolution.
     * Informs if a version was forced during the resolution process.
     * See {@link ModuleSelectionReason}
     *
     * @return information why this module version was selected.
     */
    ModuleSelectionReason getSelectionReason();
}