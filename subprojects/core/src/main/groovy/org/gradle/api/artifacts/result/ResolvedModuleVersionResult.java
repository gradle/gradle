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
 * A node in the resolved dependency graph.
 * Contains the identifier of the module and its dependencies.
 */
@Incubating
public interface ResolvedModuleVersionResult {

    /**
     * Returns the identifier of the resolved module.
     *
     * @return the identifier of the resolved module
     */
    ModuleVersionIdentifier getId();

    /**
     * Returns the dependencies of the resolved module.
     * Includes resolved and unresolved dependencies (if any).
     *
     * @return the dependencies of the resolved module
     */
    Set<? extends DependencyResult> getDependencies();

    /**
     * Returns the dependents of the resolved module.
     *
     * @return the dependents of the resolved module
     */
    Set<? extends DependencyResult> getDependents();

    /**
     * Returns the reason for selecting the module.
     * Useful if multiple candidate versions were found during dependency resolution.
     *
     * @return the reason for selecting the module
     */
    ModuleVersionSelectionReason getSelectionReason();
}