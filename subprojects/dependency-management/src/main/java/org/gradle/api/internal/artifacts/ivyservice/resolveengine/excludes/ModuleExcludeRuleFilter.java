/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;

/**
 * Manages sets of exclude rules, allowing union and intersection operations on the rules.
 *
 * <ul>
 *     <li>When a module dependency has multiple exclusions, then the resulting exclusion is the _intersection_ of those exclusions (module is excluded if excluded by _any_).</li>
 *     <li>When a module is depended on via a transitive path, then the resulting exclusion is the _intersection_ of the exclusions on each leg of the path (module is excluded if excluded by _any_).</li>
 *     <li>When a module is depended on via multiple paths in the graph, then the resulting exclusion is the _union_ of the exclusions on each of those paths (module is excluded if excluded by _all_).</li>
 * </ul>
 */
public interface ModuleExcludeRuleFilter {
    /**
     * Should this module be included in the resolution result?
     */
    boolean acceptModule(ModuleIdentifier module);

    /**
     * Should this artifact be included in the resolution result?
     */
    boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact);

    /**
     * Does this filter accept all artifacts?
     *
     * @return true if this filter returns <code>true</code> for {@link #acceptArtifact} for any provided artifact.
     */
    boolean acceptsAllArtifacts();

    /**
     * Determines if this filter accepts the same set of modules as the other.
     *
     * @return true if the filters accept the same set of modules. Returns false if they may not, or if it is unknown.
     */
    boolean excludesSameModulesAs(ModuleExcludeRuleFilter other);
}
