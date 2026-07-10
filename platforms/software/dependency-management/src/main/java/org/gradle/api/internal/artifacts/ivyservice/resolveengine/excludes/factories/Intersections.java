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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;
import org.gradle.internal.collect.PersistentSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NullMarked
class Intersections {
    private final ExcludeFactory factory;
    private final List<Intersection<? extends ExcludeSpec, ? extends ExcludeSpec>> intersections = new ArrayList<>();

    public Intersections(ExcludeFactory factory) {
        this.factory = factory;

        // For the Any intersections, be sure to add the more specific type first, so it gets used if applicable
        intersections.add(new IntersectAnyWithAny());
        intersections.add(new IntersectAnyWithBaseSpec());

        intersections.add(new IntersectGroupWithGroup());
        intersections.add(new IntersectGroupWithModuleId());
        intersections.add(new IntersectGroupWithGroupSet());
        intersections.add(new IntersectGroupWithModuleIdSet());
        intersections.add(new IntersectGroupWithModule());
        intersections.add(new IntersectGroupWithModuleSet());

        intersections.add(new IntersectGroupSetWithGroupSet());
        intersections.add(new IntersectGroupSetWithModuleId());
        intersections.add(new IntersectGroupSetWithModuleIdSet());

        intersections.add(new IntersectModuleWithModule());
        intersections.add(new IntersectModuleWithModuleId());
        intersections.add(new IntersectModuleWithModuleSet());
        intersections.add(new IntersectModuleWithModuleIdSet());
        intersections.add(new IntersectModuleWithGroupSet());

        intersections.add(new IntersectModuleIdWithModuleId());
        intersections.add(new IntersectModuleIdWithModuleIdSet());
        intersections.add(new IntersectModuleIdWithModuleSet());

        intersections.add(new IntersectModuleIdSetWithModuleIdSet());
        intersections.add(new IntersectModuleIdSetWithModuleSet());

        intersections.add(new IntersectModuleSetWithModuleSet());
        intersections.add(new IntersectModuleSetWithGroupSet());
    }

    @Nullable
    ExcludeSpec tryIntersect(ExcludeSpec left, ExcludeSpec right) {
        if (left.equals(right)) {
            return left;
        } else {
            return intersections.stream()
                .filter(i -> i.applies(left, right))
                .findFirst()
                .map(i -> i.intersect(left, right, factory))
                .orElse(null);
        }
    }

    private final class IntersectAnyWithAny extends AbstractIntersection<ExcludeAnyOf, ExcludeAnyOf> {
        public IntersectAnyWithAny() {
            super(ExcludeAnyOf.class, ExcludeAnyOf.class);
        }

        @Override
        public ExcludeSpec doIntersect(ExcludeAnyOf left, ExcludeAnyOf right, ExcludeFactory factory) {
            PersistentSet<ExcludeSpec> leftComponents = left.getComponents();
            PersistentSet<ExcludeSpec> rightComponents = right.getComponents();

            PersistentSet<ExcludeSpec> common = leftComponents.intersect(rightComponents);
            if (!common.isEmpty()) {
                ExcludeSpec alpha = factory.fromUnion(common);
                if (leftComponents.equals(common) || rightComponents.equals(common)) {
                    return alpha;
                }
                PersistentSet<ExcludeSpec> remainderLeft = leftComponents.except(common);
                PersistentSet<ExcludeSpec> remainderRight = rightComponents.except(common);

                ExcludeSpec unionLeft = factory.fromUnion(remainderLeft);
                ExcludeSpec unionRight = factory.fromUnion(remainderRight);
                ExcludeSpec beta = factory.allOf(unionLeft, unionRight);
                return factory.anyOf(alpha, beta);
            } else {
                // slowest path, full distribution
                // (A ∪ B) ∩ (C ∪ D) = (A ∩ C) ∪ (A ∩ D) ∪ (B ∩ C) ∪ (B ∩ D)
                PersistentSet<ExcludeSpec> intersections = PersistentSet.of();
                for (ExcludeSpec leftSpec : leftComponents) {
                    for (ExcludeSpec rightSpec : rightComponents) {
                        ExcludeSpec merged = tryIntersect(leftSpec, rightSpec);
                        if (merged == null) {
                            merged = factory.allOf(leftSpec, rightSpec);
                        }
                        if (!(merged instanceof ExcludeNothing)) {
                            intersections = intersections.plus(merged);
                        }
                    }
                }
                return factory.fromUnion(intersections);
            }
        }
    }

    private final class IntersectAnyWithBaseSpec extends AbstractIntersection<ExcludeAnyOf, ExcludeSpec> {
        private IntersectAnyWithBaseSpec() {
            super(ExcludeAnyOf.class, ExcludeSpec.class);
        }

        @Override
        @Nullable
        public ExcludeSpec doIntersect(ExcludeAnyOf left, ExcludeSpec right, ExcludeFactory factory) {
            // Here, we will distribute A ∩ (B ∪ C) if, and only if, at
            // least one of the distribution operations (A ∩ B) can be simplified
            ExcludeSpec[] excludeSpecs = left.getComponents().toArray(new ExcludeSpec[0]);
            @Nullable ExcludeSpec[] intersections = null;
            for (int i = 0; i < excludeSpecs.length; i++) {
                ExcludeSpec excludeSpec = tryIntersect(excludeSpecs[i], right);
                if (excludeSpec != null) {
                    if (intersections == null) {
                        intersections = new ExcludeSpec[excludeSpecs.length];
                    }
                    intersections[i] = excludeSpec;
                }
            }
            if (intersections != null) {
                PersistentSet<ExcludeSpec> simplified = PersistentSet.of();
                for (int i = 0; i < intersections.length; i++) {
                    ExcludeSpec intersection = intersections[i];
                    if (intersection instanceof ExcludeNothing) {
                        continue;
                    }
                    if (intersection != null) {
                        simplified = simplified.plus(intersection);
                    } else {
                        simplified = simplified.plus(factory.allOf(excludeSpecs[i], right));
                    }
                }
                return factory.fromUnion(simplified);
            } else {
                return null;
            }
        }

        @Override
        public boolean applies(ExcludeSpec left, ExcludeSpec right) {
            // We want to use the more specific AnyWithAny intersection if possible
            return (left instanceof ExcludeAnyOf && !(right instanceof ExcludeAnyOf))
                || (right instanceof ExcludeAnyOf && !(left instanceof ExcludeAnyOf));
        }
    }

    private static final class IntersectGroupWithGroup extends AbstractIntersection<GroupExclude, GroupExclude> {
        private IntersectGroupWithGroup() {
            super(GroupExclude.class, GroupExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupExclude left, GroupExclude right, ExcludeFactory factory) {
            // equality has been tested before, so we know groups are different
            return factory.nothing();
        }
    }

    private static final class IntersectGroupWithModuleId extends AbstractIntersection<GroupExclude, ModuleIdExclude> {
        private IntersectGroupWithModuleId() {
            super(GroupExclude.class, ModuleIdExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupExclude left, ModuleIdExclude right, ExcludeFactory factory) {
            String group = left.getGroup();
            if (right.getModuleId().getGroup().equals(group)) {
                return right;
            } else {
                return factory.nothing();
            }
        }
    }

    private static final class IntersectGroupWithGroupSet extends AbstractIntersection<GroupExclude, GroupSetExclude> {
        private IntersectGroupWithGroupSet() {
            super(GroupExclude.class, GroupSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupExclude left, GroupSetExclude right, ExcludeFactory factory) {
            String group = left.getGroup();
            if (right.getGroups().anyMatch(g -> g.equals(group))) {
                return left;
            }
            return factory.nothing();
        }
    }

    private static final class IntersectGroupWithModuleIdSet extends AbstractIntersection<GroupExclude, ModuleIdSetExclude> {
        private IntersectGroupWithModuleIdSet() {
            super(GroupExclude.class, ModuleIdSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupExclude left, ModuleIdSetExclude right, ExcludeFactory factory) {
            String group = left.getGroup();
            PersistentSet<ModuleIdentifier> moduleIds = right.getModuleIds().filter(id -> id.getGroup().equals(group));
            return factory.fromModuleIds(moduleIds);
        }
    }

    private static final class IntersectGroupWithModule extends AbstractIntersection<GroupExclude, ModuleExclude> {
        private IntersectGroupWithModule() {
            super(GroupExclude.class, ModuleExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupExclude left, ModuleExclude right, ExcludeFactory factory) {
            return factory.moduleId(DefaultModuleIdentifier.newId(left.getGroup(), right.getModule()));
        }
    }

    private static final class IntersectGroupWithModuleSet extends AbstractIntersection<GroupExclude, ModuleSetExclude> {
        private IntersectGroupWithModuleSet() {
            super(GroupExclude.class, ModuleSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupExclude left, ModuleSetExclude right, ExcludeFactory factory) {
            return factory.moduleIdSet(
                right.getModules().map(module -> DefaultModuleIdentifier.newId(left.getGroup(), module))
            );
        }
    }

    private static final class IntersectGroupSetWithGroupSet extends AbstractIntersection<GroupSetExclude, GroupSetExclude> {
        private IntersectGroupSetWithGroupSet() {
            super(GroupSetExclude.class, GroupSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupSetExclude left, GroupSetExclude right, ExcludeFactory factory) {
            PersistentSet<String> groups = left.getGroups();
            PersistentSet<String> common = right.getGroups().intersect(groups);
            return factory.fromGroups(common);
        }
    }

    private static final class IntersectGroupSetWithModuleId extends AbstractIntersection<GroupSetExclude, ModuleIdExclude> {
        private IntersectGroupSetWithModuleId() {
            super(GroupSetExclude.class, ModuleIdExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupSetExclude left, ModuleIdExclude right, ExcludeFactory factory) {
            PersistentSet<String> groups = left.getGroups();
            if (groups.contains(right.getModuleId().getGroup())) {
                return right;
            }
            return factory.nothing();
        }
    }

    private static final class IntersectGroupSetWithModuleIdSet extends AbstractIntersection<GroupSetExclude, ModuleIdSetExclude> {
        private IntersectGroupSetWithModuleIdSet() {
            super(GroupSetExclude.class, ModuleIdSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(GroupSetExclude left, ModuleIdSetExclude right, ExcludeFactory factory) {
            PersistentSet<String> groups = left.getGroups();
            PersistentSet<ModuleIdentifier> filtered = right.getModuleIds()
                .filter(id -> groups.contains(id.getGroup()));
            return factory.fromModuleIds(filtered);
        }
    }

    private static final class IntersectModuleWithModule extends AbstractIntersection<ModuleExclude, ModuleExclude> {
        private IntersectModuleWithModule() {
            super(ModuleExclude.class, ModuleExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleExclude left, ModuleExclude right, ExcludeFactory factory) {
            String module = left.getModule();
            if (right.getModule().equals(module)) {
                return left;
            } else {
                return factory.nothing();
            }
        }
    }

    private static final class IntersectModuleWithModuleId extends AbstractIntersection<ModuleExclude, ModuleIdExclude> {
        private IntersectModuleWithModuleId() {
            super(ModuleExclude.class, ModuleIdExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleExclude left, ModuleIdExclude right, ExcludeFactory factory) {
            String module = left.getModule();
            if (right.getModuleId().getName().equals(module)) {
                return right;
            } else {
                return factory.nothing();
            }
        }
    }

    private static final class IntersectModuleWithModuleSet extends AbstractIntersection<ModuleExclude, ModuleSetExclude> {
        private IntersectModuleWithModuleSet() {
            super(ModuleExclude.class, ModuleSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleExclude left, ModuleSetExclude right, ExcludeFactory factory) {
            String module = left.getModule();
            if (right.getModules().anyMatch(g -> g.equals(module))) {
                return left;
            }
            return factory.nothing();
        }
    }

    private static final class IntersectModuleWithModuleIdSet extends AbstractIntersection<ModuleExclude, ModuleIdSetExclude> {
        private IntersectModuleWithModuleIdSet() {
            super(ModuleExclude.class, ModuleIdSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleExclude left, ModuleIdSetExclude right, ExcludeFactory factory) {
            String module = left.getModule();
            PersistentSet<ModuleIdentifier> common = right.getModuleIds().filter(id -> id.getName().equals(module));
            return factory.fromModuleIds(common);
        }
    }

    private static final class IntersectModuleIdWithModuleId extends AbstractIntersection<ModuleIdExclude, ModuleIdExclude> {
        private IntersectModuleIdWithModuleId() {
            super(ModuleIdExclude.class, ModuleIdExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleIdExclude left, ModuleIdExclude right, ExcludeFactory factory) {
            if (left.equals(right)) {
                return left;
            }
            return factory.nothing();
        }
    }

    private static final class IntersectModuleIdWithModuleIdSet extends AbstractIntersection<ModuleIdExclude, ModuleIdSetExclude> {
        private IntersectModuleIdWithModuleIdSet() {
            super(ModuleIdExclude.class, ModuleIdSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleIdExclude left, ModuleIdSetExclude right, ExcludeFactory factory) {
            PersistentSet<ModuleIdentifier> rightModuleIds = right.getModuleIds();
            if (rightModuleIds.contains(left.getModuleId())) {
                return left;
            }
            return factory.nothing();
        }
    }

    private static final class IntersectModuleIdWithModuleSet extends AbstractIntersection<ModuleIdExclude, ModuleSetExclude> {
        private IntersectModuleIdWithModuleSet() {
            super(ModuleIdExclude.class, ModuleSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleIdExclude left, ModuleSetExclude right, ExcludeFactory factory) {
            if (right.getModules().contains(left.getModuleId().getName())) {
                return left;
            } else {
                return factory.nothing();
            }
        }
    }

    private static final class IntersectModuleIdSetWithModuleIdSet extends AbstractIntersection<ModuleIdSetExclude, ModuleIdSetExclude> {
        private IntersectModuleIdSetWithModuleIdSet() {
            super(ModuleIdSetExclude.class, ModuleIdSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleIdSetExclude left, ModuleIdSetExclude right, ExcludeFactory factory) {
            PersistentSet<ModuleIdentifier> moduleIds = left.getModuleIds();
            PersistentSet<ModuleIdentifier> common = right.getModuleIds().intersect(moduleIds);
            return factory.fromModuleIds(common);
        }
    }

    private static final class IntersectModuleIdSetWithModuleSet extends AbstractIntersection<ModuleIdSetExclude, ModuleSetExclude> {
        private IntersectModuleIdSetWithModuleSet() {
            super(ModuleIdSetExclude.class, ModuleSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleIdSetExclude left, ModuleSetExclude right, ExcludeFactory factory) {
            PersistentSet<ModuleIdentifier> moduleIds = left.getModuleIds();
            PersistentSet<String> modules = right.getModules();
            PersistentSet<ModuleIdentifier> identifiers = moduleIds
                .filter(e -> modules.contains(e.getName()));
            if (identifiers.isEmpty()) {
                return factory.nothing();
            }
            if (identifiers.size() == 1) {
                return factory.moduleId(identifiers.iterator().next());
            } else {
                return factory.moduleIdSet(identifiers);
            }
        }
    }

    private static final class IntersectModuleSetWithModuleSet extends AbstractIntersection<ModuleSetExclude, ModuleSetExclude> {
        private IntersectModuleSetWithModuleSet() {
            super(ModuleSetExclude.class, ModuleSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleSetExclude left, ModuleSetExclude right, ExcludeFactory factory) {
            PersistentSet<String> modules = left.getModules().intersect(right.getModules());
            if (modules.isEmpty()) {
                return factory.nothing();
            }
            if (modules.size() == 1) {
                return factory.module(modules.iterator().next());
            }
            return factory.moduleSet(modules);
        }
    }

    private static final class IntersectModuleSetWithGroupSet extends AbstractIntersection<ModuleSetExclude, GroupSetExclude> {
        private IntersectModuleSetWithGroupSet() {
            super(ModuleSetExclude.class, GroupSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleSetExclude left, GroupSetExclude right, ExcludeFactory factory) {
            return factory.moduleIdSet(right.getGroups().flatMap(group -> left.getModules().map(module -> DefaultModuleIdentifier.newId(group, module))));
        }
    }

    private static final class IntersectModuleWithGroupSet extends AbstractIntersection<ModuleExclude, GroupSetExclude> {
        private IntersectModuleWithGroupSet() {
            super(ModuleExclude.class, GroupSetExclude.class);
        }

        @Override
        public ExcludeSpec doIntersect(ModuleExclude left, GroupSetExclude right, ExcludeFactory factory) {
            return factory.moduleIdSet(right.getGroups().map(group -> DefaultModuleIdentifier.newId(group, left.getModule())));
        }
    }
}
