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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.Intersections;

public interface ExcludeAnyOf extends CompositeExclude {
    @Override
    default ExcludeSpec intersect(ExcludeAnyOf other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ArtifactExclude other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ExcludeAllOf other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ExcludeEverything other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ExcludeNothing other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(GroupExclude other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(GroupSetExclude other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ModuleExclude other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ModuleIdExclude other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ModuleIdSetExclude other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ModuleSetExclude other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec intersect(ExcludeSpec other, ExcludeFactory factory) {
        return Intersections.doIntersect(this, other, factory);
    }

    @Override
    default ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }
}
