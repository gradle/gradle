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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.Intersections;

import java.util.Set;

public interface ExcludeAnyOf extends CompositeExclude {
    @Override
    default ExcludeSpec intersect(ExcludeAnyOf right, ExcludeFactory factory) {
        Set<ExcludeSpec> leftComponents = this.getComponents();
        Set<ExcludeSpec> rightComponents = right.getComponents();
        Set<ExcludeSpec> common = Sets.newHashSet(leftComponents);
        common.retainAll(rightComponents);
        if (common.size() >= 1) {
            ExcludeSpec alpha = Intersections.asUnion(common, factory);
            if (leftComponents.equals(common) || rightComponents.equals(common)) {
                return alpha;
            }
            Set<ExcludeSpec> remainderLeft = Sets.newHashSet(leftComponents);
            remainderLeft.removeAll(common);
            Set<ExcludeSpec> remainderRight = Sets.newHashSet(rightComponents);
            remainderRight.removeAll(common);

            ExcludeSpec unionLeft = Intersections.asUnion(remainderLeft, factory);
            ExcludeSpec unionRight = Intersections.asUnion(remainderRight, factory);
            ExcludeSpec beta = factory.allOf(unionLeft, unionRight);
            return factory.anyOf(alpha, beta);
        } else {
            // slowest path, full distribution
            // (A ∪ B) ∩ (C ∪ D) = (A ∩ C) ∪ (A ∩ D) ∪ (B ∩ C) ∪ (B ∩ D)
            Set<ExcludeSpec> intersections = Sets.newHashSetWithExpectedSize(leftComponents.size() * rightComponents.size());
            for (ExcludeSpec leftSpec : leftComponents) {
                for (ExcludeSpec rightSpec : rightComponents) {
                    ExcludeSpec merged = Intersections.tryIntersect(leftSpec, rightSpec, factory);
                    //ExcludeSpec merged = leftSpec.beginIntersect(rightSpec, factory);
                    if (merged == null) {
                        merged = factory.allOf(leftSpec, rightSpec);
                    }
                    if (!(merged instanceof ExcludeNothing)) {
                        intersections.add(merged);
                    }
                }
            }
            return Intersections.asUnion(intersections, factory);
        }
    }

    default ExcludeSpec intersect(ExcludeSpec right, ExcludeFactory factory) {
        Set<ExcludeSpec> leftComponents = this.getComponents();
        // Here, we will distribute A ∩ (B ∪ C) if, and only if, at
        // least one of the distribution operations (A ∩ B) can be simplified
        ExcludeSpec[] excludeSpecs = leftComponents.toArray(new ExcludeSpec[0]);
        ExcludeSpec[] intersections = null;
        for (int i = 0; i < excludeSpecs.length; i++) {
            ExcludeSpec excludeSpec = Intersections.tryIntersect(excludeSpecs[i], right, factory);
            //ExcludeSpec excludeSpec = excludeSpecs[i].beginIntersect(right, factory);
            if (excludeSpec != null) {
                if (intersections == null) {
                    intersections = new ExcludeSpec[excludeSpecs.length];
                }
                intersections[i] = excludeSpec;
            }
        }
        if (intersections != null) {
            Set<ExcludeSpec> simplified = Sets.newHashSetWithExpectedSize(excludeSpecs.length);
            for (int i = 0; i < intersections.length; i++) {
                ExcludeSpec intersection = intersections[i];
                if (intersection instanceof ExcludeNothing) {
                    continue;
                }
                if (intersection != null) {
                    simplified.add(intersection);
                } else {
                    simplified.add(factory.allOf(excludeSpecs[i], right));
                }
            }
            return Intersections.asUnion(simplified, factory);
        } else {
            return null;
        }
    }

    /**
     * Called if no more specific overload found and returns the default result: nothing.
     */
    @Override
    default ExcludeSpec beginIntersect(ExcludeSpec other, ExcludeFactory factory) {
        return other.intersect(this, factory);
    }
}
