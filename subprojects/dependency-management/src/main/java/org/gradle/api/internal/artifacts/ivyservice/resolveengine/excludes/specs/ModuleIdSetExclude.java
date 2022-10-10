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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.Intersections;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

public interface ModuleIdSetExclude extends ExcludeSpec {
    Set<ModuleIdentifier> getModuleIds();

    @Override
    default ExcludeSpec intersect(ModuleIdSetExclude right, ExcludeFactory factory) {
        Set<ModuleIdentifier> moduleIds = this.getModuleIds();
        Set<ModuleIdentifier> common = Sets.newHashSet(right.getModuleIds());
        common.retainAll(moduleIds);
        return Intersections.moduleIds(common, factory);
    }

    @Override
    default ExcludeSpec intersect(ModuleSetExclude right, ExcludeFactory factory) {
        Set<ModuleIdentifier> moduleIds = this.getModuleIds();
        Set<String> modules = right.getModules();
        Set<ModuleIdentifier> identifiers = moduleIds.stream()
                .filter(e -> modules.contains(e.getName()))
                .collect(toSet());
        if (identifiers.isEmpty()) {
            return factory.nothing();
        }
        if (identifiers.size() == 1) {
            return factory.moduleId(identifiers.iterator().next());
        } else {
            return factory.moduleIdSet(identifiers);
        }
    }

    @Override
    default ExcludeSpec intersect(GroupExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // Reverse call - implemented on other side
    }

    @Override
    default ExcludeSpec intersect(ModuleExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // Reverse call - implemented on other side
    }

    @Override
    default ExcludeSpec intersect(GroupSetExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // Reverse call - implemented on other side
    }

    @Override
    default ExcludeSpec intersect(ModuleIdExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // Reverse call - implemented on other side
    }

    /**
     * Called if no more specific overload found and returns the default result: nothing.
     */
    @Override
    default ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }
}
