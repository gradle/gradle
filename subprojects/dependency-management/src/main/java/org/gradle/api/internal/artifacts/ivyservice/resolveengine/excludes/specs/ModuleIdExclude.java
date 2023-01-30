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

import java.util.Set;

public interface ModuleIdExclude extends ExcludeSpec {
    ModuleIdentifier getModuleId();

    @Override
    default ExcludeSpec intersect(ModuleIdExclude right, ExcludeFactory factory) {
        if (this.equals(right)) {
            return this;
        }
        return factory.nothing();
    }

    @Override
    default ExcludeSpec intersect(ModuleSetExclude right, ExcludeFactory factory) {
        if (right.getModules().contains(this.getModuleId().getName())) {
            return this;
        } else {
            return factory.nothing();
        }
    }

    @Override
    default ExcludeSpec intersect(ModuleIdSetExclude right, ExcludeFactory factory) {
        Set<ModuleIdentifier> rightModuleIds = right.getModuleIds();
        if (rightModuleIds.contains(this.getModuleId())) {
            return this;
        }
        return factory.nothing();
    }

    @Override
    default ExcludeSpec intersect(ModuleExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // We implemented the equivalent reverse of this call, forward to that
    }

    @Override
    default ExcludeSpec intersect(GroupExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // We implemented the equivalent reverse of this call, forward to that
    }

    @Override
    default ExcludeSpec intersect(GroupSetExclude other, ExcludeFactory factory) {
        return other.intersect(this, factory); // We implemented the equivalent reverse of this call, forward to that
    }

    @Override
    default ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }
}
