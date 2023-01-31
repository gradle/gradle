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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

public interface GroupSetExclude extends ExcludeSpec {
    Set<String> getGroups();

    @Override
    default ExcludeSpec intersect(GroupSetExclude right, ExcludeFactory factory) {
        Set<String> groups = this.getGroups();
        Set<String> common = Sets.newHashSet(right.getGroups());
        common.retainAll(groups);
        return factory.fromGroups(common);
    }

    @Override
    default ExcludeSpec intersect(ModuleIdExclude right, ExcludeFactory factory) {
        Set<String> groups = this.getGroups();
        if (groups.contains(right.getModuleId().getGroup())) {
            return right;
        }
        return factory.nothing();
    }

    @Override
    default ExcludeSpec intersect(ModuleIdSetExclude right, ExcludeFactory factory) {
        Set<String> groups = this.getGroups();
        Set<ModuleIdentifier> filtered = right.getModuleIds()
                .stream()
                .filter(id -> groups.contains(id.getGroup()))
                .collect(toSet());
        return factory.fromModuleIds(filtered);
    }

    @Override
    default ExcludeSpec intersect(ModuleSetExclude other, ExcludeFactory factory) {
        return factory.moduleIdSet(this.getGroups().stream().flatMap(group -> other.getModules().stream().map(module -> DefaultModuleIdentifier.newId(group, module))).collect(toSet()));
    }

    @Override
    default ExcludeSpec intersect(GroupExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // We implemented the equivalent reverse of this call, forward to that
    }

    @Override
    default ExcludeSpec intersect(ModuleExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // We implemented the equivalent reverse of this call, forward to that
    }

    @Override
    default ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }
}
