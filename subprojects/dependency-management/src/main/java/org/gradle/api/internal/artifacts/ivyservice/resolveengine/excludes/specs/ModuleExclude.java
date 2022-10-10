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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.Intersections;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

public interface ModuleExclude extends ExcludeSpec {
    String getModule();

    @Override
    default ExcludeSpec intersect(ModuleExclude right, ExcludeFactory factory) {
        String module = this.getModule();
        if (right.getModule().equals(module)) {
            return this;
        } else {
            return factory.nothing();
        }
    }

    @Override
    default ExcludeSpec intersect(ModuleIdExclude right, ExcludeFactory factory) {
        String module = this.getModule();
        if (right.getModuleId().getName().equals(module)) {
            return right;
        } else {
            return factory.nothing();
        }
    }

    @Override
    default ExcludeSpec intersect(ModuleSetExclude right, ExcludeFactory factory) {
        String module = this.getModule();
        if (right.getModules().stream().anyMatch(g -> g.equals(module))) {
            return this;
        }
        return factory.nothing();
    }

    @Override
    default ExcludeSpec intersect(ModuleIdSetExclude right, ExcludeFactory factory) {
        String module = this.getModule();
        Set<ModuleIdentifier> common = right.getModuleIds().stream().filter(id -> id.getName().equals(module)).collect(toSet());
        return factory.fromModuleIds(common);
    }

    @Override
    default ExcludeSpec intersect(GroupExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // Reverse call - implemented on other side
    }

    @Override
    default ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }
}
