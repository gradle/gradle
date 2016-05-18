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
 */
public interface ModuleExclusion {
    /**
     * Should this module be excluded from the resolution result?
     */
    boolean excludeModule(ModuleIdentifier module);

    /**
     * Should this artifact be excluded from the resolution result?
     */
    boolean excludeArtifact(ModuleIdentifier module, IvyArtifactName artifact);

    /**
     * Could any artifacts be excluded by this filter?
     *
     * @return false if this filter could return <code>false</code> for {@link #excludeArtifact} for every provided artifact.
     */
    boolean mayExcludeArtifacts();

    /**
     * Determines if this filter excludes the same set of modules as the other.
     *
     * @return true if the filters excludes the same set of modules. Returns false if they may not, or if it is unknown.
     */
    boolean excludesSameModulesAs(ModuleExclusion other);
}
