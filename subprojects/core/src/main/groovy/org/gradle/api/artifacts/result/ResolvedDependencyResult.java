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
import org.gradle.api.artifacts.ModuleVersionSelector;

/**
 * Resolved dependency result is an edge in the resolved dependency graph.
 * Provides information about the requested module version and the selected module version.
 * Requested differs from selected due to number of factors,
 * for example conflict resolution, forcing particular version or when dynamic versions are used.
 * For information about those terms please refer to the user guide.
 */
@Incubating
public interface ResolvedDependencyResult {

    /**
     * Returns the requested module version.
     *
     * @return requested module version
     */
    ModuleVersionSelector getRequested();

    /**
     * Returns the selected module version.
     *
     * @return selected module version
     */
    ResolvedModuleVersionResult getSelected();
}
