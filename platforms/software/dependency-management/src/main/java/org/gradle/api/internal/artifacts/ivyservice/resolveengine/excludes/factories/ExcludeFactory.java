/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Set;

public interface ExcludeFactory {
    ExcludeNothing nothing();

    ExcludeEverything everything();

    GroupExclude group(String group);

    ModuleExclude module(String module);

    ModuleIdExclude moduleId(ModuleIdentifier id);

    ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two);

    ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two);

    ExcludeSpec anyOf(Set<ExcludeSpec> specs);

    ExcludeSpec allOf(Set<ExcludeSpec> specs);

    ExcludeSpec ivyPatternExclude(ModuleIdentifier moduleId, IvyArtifactName artifact, String matcher);

    ModuleIdSetExclude moduleIdSet(Set<ModuleIdentifier> modules);

    GroupSetExclude groupSet(Set<String> groups);

    ModuleSetExclude moduleSet(Set<String> modules);

    default ExcludeSpec fromUnion(Set<ExcludeSpec> remainder) {
        if (remainder.isEmpty()) {
            // It's an intersection, and this method is always called on the remainder
            // of a reduction operation. If the remainder is empty then it means that
            // the intersection is empty
            return nothing();
        }
        return remainder.size() == 1 ? remainder.iterator().next() : anyOf(remainder);
    }

    default ExcludeSpec fromModuleIds(Set<ModuleIdentifier> common) {
        if (common.isEmpty()) {
            return nothing();
        }
        if (common.size() == 1) {
            return moduleId(common.iterator().next());
        }
        return moduleIdSet(common);
    }

    default ExcludeSpec fromModules(Set<ModuleIdentifier> moduleIds) {
        if (moduleIds.isEmpty()) {
            return nothing();
        }
        if (moduleIds.size() == 1) {
            return moduleId(moduleIds.iterator().next());
        }
        return moduleIdSet(moduleIds);
    }

    default ExcludeSpec fromGroups(Set<String> common) {
        if (common.isEmpty()) {
            return nothing();
        }
        if (common.size() == 1) {
            return group(common.iterator().next());
        }
        return groupSet(common);
    }
}
