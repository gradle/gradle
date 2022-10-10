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

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.Intersections;

import static java.util.stream.Collectors.toSet;

public interface GroupExclude extends ExcludeSpec {
    String getGroup();

    @Override
    default ExcludeSpec intersect(GroupExclude right, ExcludeFactory factory) {
        String group = this.getGroup();
        // equality has been tested before so we know groups are different
        return factory.nothing();
    }

    @Override
    default ExcludeSpec intersect(ModuleIdExclude right, ExcludeFactory factory) {
        String group = this.getGroup();
        if (right.getModuleId().getGroup().equals(group)) {
            return right;
        } else {
            return factory.nothing();
        }
    }

    @Override
    default ExcludeSpec intersect(GroupSetExclude right, ExcludeFactory factory) {
        String group = this.getGroup();
        if (right.getGroups().stream().anyMatch(g -> g.equals(group))) {
            return this;
        }
        return factory.nothing();
    }

    @Override
    default ExcludeSpec intersect(ModuleIdSetExclude right, ExcludeFactory factory) {
        return Intersections.doIntersect(this, right, factory);
    }

    @Override
    default ExcludeSpec intersect(ModuleExclude right, ExcludeFactory factory) {
        String group = this.getGroup();
        return factory.moduleId(DefaultModuleIdentifier.newId(this.getGroup(), right.getModule()));
    }

    @Override
    default ExcludeSpec intersect(ModuleSetExclude right, ExcludeFactory factory) {
        String group = this.getGroup();
        return factory.moduleIdSet(right.getModules()
                .stream()
                .map(module -> DefaultModuleIdentifier.newId(this.getGroup(), module))
                .collect(toSet())
        );
    }

    @Override
    default ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }
}
