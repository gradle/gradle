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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.CompositeExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAllOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

/**
 * This factory performs normalization of exclude rules. This is the smartest
 * of all factories and is responsible for doing some basic algebra computations.
 * It shouldn't be too slow, or the whole chain will pay the price.
 */
public class NormalizingExcludeFactory extends DelegatingExcludeFactory {
    public NormalizingExcludeFactory(ExcludeFactory delegate) {
        super(delegate);
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return doUnion(ImmutableList.of(one, two));
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return doIntersect(ImmutableList.of(one, two));
    }

    @Override
    public ExcludeSpec anyOf(List<ExcludeSpec> specs) {
        return doUnion(specs);
    }

    @Override
    public ExcludeSpec allOf(List<ExcludeSpec> specs) {
        return doIntersect(specs);
    }

    private ExcludeSpec doUnion(List<ExcludeSpec> specs) {
        Set<ExcludeSpec> flattened = flatten(ExcludeAnyOf.class, specs, ExcludeEverything.class::isInstance, ExcludeNothing.class::isInstance);
        if (flattened == null) {
            return everything();
        }
        if (flattened.isEmpty()) {
            return nothing();
        }
        Map<UnionOf, List<ExcludeSpec>> byType = flattened.stream().collect(Collectors.groupingBy(UnionOf::typeOf));
        List<ModuleIdExclude> moduleIdExcludes = UnionOf.MODULEID.fromMap(byType);
        List<ModuleIdSetExclude> moduleIdSetsExcludes = UnionOf.MODULEID_SET.fromMap(byType);
        List<GroupExclude> groupExcludes = UnionOf.GROUP.fromMap(byType);
        List<GroupSetExclude> groupSetExcludes = UnionOf.GROUP_SET.fromMap(byType);
        List<ModuleExclude> moduleExcludes = UnionOf.MODULE.fromMap(byType);
        List<ModuleSetExclude> moduleSetExcludes = UnionOf.MODULE_SET.fromMap(byType);
        List<ExcludeSpec> other = UnionOf.NOT_JOINABLE.fromMap(byType);
        if (!moduleIdExcludes.isEmpty()) {
            if (moduleIdExcludes.size() > 1 || !moduleIdSetsExcludes.isEmpty()) {
                ModuleIdSetExclude excludeSpec = delegate.moduleIdSet(moduleIdExcludes.stream().map(ModuleIdExclude::getModuleId).collect(toSet()));
                if (moduleIdSetsExcludes.isEmpty()) {
                    moduleIdSetsExcludes = ImmutableList.of(excludeSpec);
                } else {
                    moduleIdSetsExcludes.add(excludeSpec);
                }
                moduleIdExcludes = Collections.emptyList();
            }
        }
        if (!groupExcludes.isEmpty()) {
            if (groupExcludes.size() > 1 || !groupSetExcludes.isEmpty()) {
                GroupSetExclude excludeSpec = delegate.groupSet(groupExcludes.stream().map(GroupExclude::getGroup).collect(toSet()));
                if (groupSetExcludes.isEmpty()) {
                    groupSetExcludes = ImmutableList.of(excludeSpec);
                } else {
                    groupSetExcludes.add(excludeSpec);
                }
                groupExcludes = Collections.emptyList();
            }
        }
        if (!moduleExcludes.isEmpty()) {
            if (moduleExcludes.size() > 1 || !moduleSetExcludes.isEmpty()) {
                ModuleSetExclude excludeSpec = delegate.moduleSet(moduleExcludes.stream().map(ModuleExclude::getModule).collect(toSet()));
                if (moduleSetExcludes.isEmpty()) {
                    moduleSetExcludes = ImmutableList.of(excludeSpec);
                } else {
                    moduleSetExcludes.add(excludeSpec);
                }
                moduleExcludes = Collections.emptyList();
            }
        }
        if (moduleIdSetsExcludes.size() > 1) {
            moduleIdSetsExcludes = ImmutableList.of(delegate.moduleIdSet(moduleIdSetsExcludes.stream().flatMap(e -> e.getModuleIds().stream()).collect(toSet())));
        }
        if (groupSetExcludes.size() > 1) {
            groupSetExcludes = ImmutableList.of(delegate.groupSet(groupSetExcludes.stream().flatMap(e -> e.getGroups().stream()).collect(toSet())));
        }
        if (moduleSetExcludes.size() > 1) {
            moduleSetExcludes = ImmutableList.of(delegate.moduleSet(moduleSetExcludes.stream().flatMap(e -> e.getModules().stream()).collect(toSet())));
        }
        ImmutableList.Builder<ExcludeSpec> builder = ImmutableList.builderWithExpectedSize(
            moduleIdExcludes.size() + groupExcludes.size() + moduleExcludes.size() +
                moduleIdSetsExcludes.size() + groupSetExcludes.size() + moduleSetExcludes.size() + other.size()
        );
        builder.addAll(moduleIdExcludes);
        builder.addAll(groupExcludes);
        builder.addAll(moduleExcludes);
        builder.addAll(moduleIdSetsExcludes);
        builder.addAll(groupSetExcludes);
        builder.addAll(moduleSetExcludes);
        builder.addAll(other);
        return Optimizations.optimizeList(this, builder.build(), delegate::anyOf);
    }

    /**
     * Flattens a collection of elements that are going to be joined or intersected. There
     * are 3 possible outcomes:
     * - null means that the fast exit condition was reached, meaning that the caller knows it's not worth computing more
     * - empty list meaning that an easy simplification was reached and we directly know the result
     * - flattened unions/intersections
     */
    private <T extends ExcludeSpec> Set<ExcludeSpec> flatten(Class<T> flattenType, List<ExcludeSpec> specs, Predicate<ExcludeSpec> fastExit, Predicate<ExcludeSpec> filter) {
        Set<ExcludeSpec> out = null;
        for (ExcludeSpec spec : specs) {
            if (fastExit.test(spec)) {
                return null;
            }
            if (!filter.test(spec)) {
                if (out == null) {
                    out = Sets.newHashSetWithExpectedSize(4 * specs.size());
                }
                if (flattenType.isInstance(spec)) {
                    out.addAll(((CompositeExclude) spec).getComponents());
                } else {
                    out.add(spec);
                }
            }
        }
        return out == null ? Collections.emptySet() : out;
    }

    private ExcludeSpec doIntersect(List<ExcludeSpec> specs) {
        Set<ExcludeSpec> relevant = flatten(ExcludeAllOf.class, specs, ExcludeNothing.class::isInstance, ExcludeEverything.class::isInstance);
        if (relevant == null) {
            return nothing();
        }
        if (relevant.isEmpty()) {
            return everything();
        }
        return Optimizations.optimizeList(this, ImmutableList.copyOf(relevant), delegate::allOf);
    }

    private enum UnionOf {
        MODULEID(ModuleIdExclude.class),
        GROUP(GroupExclude.class),
        MODULE(ModuleExclude.class),
        MODULEID_SET(ModuleIdSetExclude.class),
        GROUP_SET(GroupSetExclude.class),
        MODULE_SET(ModuleSetExclude.class),
        NOT_JOINABLE(ExcludeSpec.class);

        private final Class<? extends ExcludeSpec> excludeClass;

        UnionOf(Class<? extends ExcludeSpec> excludeClass) {
            this.excludeClass = excludeClass;
        }

        public <T extends ExcludeSpec> List<T> fromMap(Map<UnionOf, List<ExcludeSpec>> from) {
            return Cast.uncheckedCast(from.getOrDefault(this, Collections.emptyList()));
        }

        public static UnionOf typeOf(ExcludeSpec spec) {
            for (UnionOf unionOf : UnionOf.values()) {
                if (unionOf.excludeClass.isInstance(spec)) {
                    return unionOf;
                }
            }
            return null;
        }
    }
}
