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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.CompositeExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAllOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;
import org.gradle.internal.Cast;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * This factory performs normalization of exclude rules. This is the smartest
 * of all factories and is responsible for doing some basic algebra computations.
 * It shouldn't be too slow, or the whole chain will pay the price.
 */
public class NormalizingExcludeFactory extends DelegatingExcludeFactory {
    private final Intersections intersections;
    private final Unions unions;

    public NormalizingExcludeFactory(ExcludeFactory delegate) {
        super(delegate);
        this.intersections = new Intersections(this);
        this.unions = new Unions(this);
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return simplify(ExcludeAllOf.class, one, two, (left, right) -> doUnion(ImmutableSet.of(left, right)));
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return simplify(ExcludeAnyOf.class, one, two, (left, right) -> doIntersect(ImmutableSet.of(left, right)));
    }

    // Simplifies (A ∪ ...) ∩ A = A
    // and  (A ∩ ...) ∪ A = A
    private ExcludeSpec simplify(Class<? extends CompositeExclude> clazz, ExcludeSpec one, ExcludeSpec two, BiFunction<ExcludeSpec, ExcludeSpec, ExcludeSpec> orElse) {
        if (clazz.isInstance(one)) {
            if (componentsOf(one).contains(two)) {
                return two;
            }
        }
        if (clazz.isInstance(two)) {
            if (componentsOf(two).contains(one)) {
                return one;
            }
        }
        return orElse.apply(one, two);
    }

    // A ∩ (A ∪ B) ∩ (A ∪ C) -> A
    // A ∪ (A ∩ B) ∪ (A ∩ C) -> A
    private Set<ExcludeSpec> simplifySet(Class<? extends CompositeExclude> clazz, Set<ExcludeSpec> specs) {
        if (specs.stream().noneMatch(clazz::isInstance)) {
            return specs;
        }
        ExcludeSpec[] asArray = specs.toArray(new ExcludeSpec[0]);
        boolean doDrop = false;
        for (int i = 0; i < asArray.length; i++) {
            ExcludeSpec excludeSpec = asArray[i];
            if (clazz.isInstance(excludeSpec)) {
                Set<ExcludeSpec> components = componentsOf(excludeSpec);
                for (int j = 0; j < asArray.length; j++) {
                    if (i != j && components.contains(asArray[j])) {
                        doDrop = true;
                        asArray[i] = null;
                        break;
                    }
                }
            }
        }
        if (doDrop) {
            specs = Arrays.stream(asArray).filter(Objects::nonNull).collect(toSet());
        }
        return specs;
    }

    private Set<ExcludeSpec> componentsOf(ExcludeSpec spec) {
        return ((CompositeExclude) spec).getComponents();
    }

    @Override
    public ExcludeSpec anyOf(Set<ExcludeSpec> specs) {
        return doUnion(specs);
    }

    @Override
    public ExcludeSpec allOf(Set<ExcludeSpec> specs) {
        return doIntersect(specs);
    }

    private ExcludeSpec doUnion(Set<ExcludeSpec> specs) {
        specs = simplifySet(ExcludeAllOf.class, specs);
        FlattenOperationResult flattened = flatten(ExcludeAnyOf.class, specs, ExcludeEverything.class::isInstance, ExcludeNothing.class::isInstance);
        if (flattened.fastExit) {
            return everything();
        }
        if (flattened.result.isEmpty()) {
            return nothing();
        }
        Map<UnionOf, List<ExcludeSpec>> byType = flattened.result.stream().collect(Collectors.groupingBy(UnionOf::typeOf));
        List<ModuleIdExclude> moduleIdExcludes = UnionOf.MODULEID.fromMap(byType);
        List<ModuleIdSetExclude> moduleIdSetsExcludes = UnionOf.MODULEID_SET.fromMap(byType);
        List<GroupExclude> groupExcludes = UnionOf.GROUP.fromMap(byType);
        List<GroupSetExclude> groupSetExcludes = UnionOf.GROUP_SET.fromMap(byType);
        List<ModuleExclude> moduleExcludes = UnionOf.MODULE.fromMap(byType);
        List<ModuleSetExclude> moduleSetExcludes = UnionOf.MODULE_SET.fromMap(byType);
        List<ExcludeSpec> other = UnionOf.NOT_JOINABLE.fromMap(byType);
        if (!moduleIdExcludes.isEmpty()) {
            // If there's more than one module id, merge them into a module id set
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
            // If there's more than group, merge them into a group set
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
            // If there's more than one module, merge them into a module set
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
        ImmutableSet.Builder<ExcludeSpec> builder = ImmutableSet.builderWithExpectedSize(
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
        Set<ExcludeSpec> elements = builder.build();
        if (elements.size() > 1) {
            // try simplify
            ExcludeSpec[] asArray = elements.toArray(new ExcludeSpec[0]);
            boolean simplified = false;
            for (int i = 0; i < asArray.length; i++) {
                ExcludeSpec left = asArray[i];
                if (left != null) {
                    for (int j = 0; j < asArray.length; j++) {
                        ExcludeSpec right = asArray[j];
                        if (right != null && i != j) {
                            ExcludeSpec merged = unions.tryUnion(left, right);
                            if (merged != null) {
                                if (merged instanceof ExcludeEverything) {
                                    return merged;
                                }
                                left = merged;
                                asArray[i] = merged;
                                asArray[j] = null;
                                simplified = true;
                            }
                        }
                    }
                }
            }
            if (simplified) {
                elements = Arrays.stream(asArray).filter(Objects::nonNull).collect(toSet());
            }
        }
        if (elements.size() == 2) {
            // Corner case to handle one of the two elements being an anyOf
            Iterator<ExcludeSpec> specIterator = elements.iterator();
            ExcludeSpec first = specIterator.next();
            ExcludeSpec second = specIterator.next();

            if (first instanceof ExcludeAnyOf || second instanceof ExcludeAnyOf) {
                ImmutableSet.Builder<ExcludeSpec> newBuilder = ImmutableSet.builder();
                if (first instanceof ExcludeAnyOf) {
                    newBuilder.addAll(((ExcludeAnyOf)first).getComponents());
                } else {
                    builder.add(first);
                }
                if (second instanceof ExcludeAnyOf) {
                    newBuilder.addAll(((ExcludeAnyOf)second).getComponents());
                } else {
                    builder.add(second);
                }
                elements = builder.build();
            }
        }
        return Optimizations.optimizeCollection(this, elements, delegate::anyOf);
    }

    /**
     * Flattens a collection of elements that are going to be joined or intersected. There
     * are 3 possible outcomes:
     * - null means that the fast exit condition was reached, meaning that the caller knows it's not worth computing more
     * - empty list meaning that an easy simplification was reached and we directly know the result
     * - flattened unions/intersections
     */
    private <T extends ExcludeSpec> FlattenOperationResult flatten(Class<T> flattenType, Set<ExcludeSpec> specs, Predicate<ExcludeSpec> fastExit, Predicate<ExcludeSpec> ignoreSpec) {
        boolean filtered = false;
        boolean flatten = false;
        int size = 0;
        for (ExcludeSpec spec : specs) {
            if (fastExit.test(spec)) {
                return FlattenOperationResult.FAST_EXIT;
            }
            if (ignoreSpec.test(spec)) {
                filtered = true;
            } else if (flattenType.isInstance(spec)) {
                flatten = true;
                size += ((CompositeExclude) spec).size();
            } else {
                size++;
            }
        }
        if (!filtered && !flatten) {
            return FlattenOperationResult.of(specs);
        }
        if (filtered && !flatten) {
            return filterOnly(specs, ignoreSpec);
        }
        // slowest path
        return expensiveFlatten(flattenType, maybeFilter(specs, ignoreSpec, filtered), size);
    }

    private FlattenOperationResult filterOnly(Set<ExcludeSpec> specs, Predicate<ExcludeSpec> ignoreSpec) {
        return FlattenOperationResult.of(specs.stream().filter(e -> !ignoreSpec.test(e)).collect(toSet()));
    }

    private Stream<ExcludeSpec> maybeFilter(Set<ExcludeSpec> specs, Predicate<ExcludeSpec> ignoreSpec, boolean filtered) {
        Stream<ExcludeSpec> stream = specs.stream();
        if (filtered) {
            stream = stream.filter(e -> !ignoreSpec.test(e));
        }
        return stream;
    }

    private <T extends ExcludeSpec> FlattenOperationResult expensiveFlatten(Class<T> flattenType, Stream<ExcludeSpec> stream, int size) {
        return FlattenOperationResult.of(stream
            .flatMap(e -> {
                if (flattenType.isInstance(e)) {
                    CompositeExclude compositeExclude = (CompositeExclude) e;
                    return compositeExclude.getComponents().stream();
                }
                return Stream.of(e);
            })
            .collect(Collectors.toCollection(() -> Sets.newHashSetWithExpectedSize(size)))
        );
    }

    private ExcludeSpec doIntersect(Set<ExcludeSpec> specs) {
        specs = simplifySet(ExcludeAnyOf.class, specs);
        FlattenOperationResult flattened = flatten(ExcludeAllOf.class, specs, ExcludeNothing.class::isInstance, ExcludeEverything.class::isInstance);
        if (flattened.fastExit) {
            return nothing();
        }
        Set<ExcludeSpec> result = flattened.result;
        if (result.isEmpty()) {
            return everything();
        }
        if (result.size() > 1) {
            // try simplify
            ExcludeSpec[] asArray = result.toArray(new ExcludeSpec[0]);
            boolean simplified = false;
            for (int i = 0; i < asArray.length; i++) {
                ExcludeSpec left = asArray[i];
                if (left != null) {
                    for (int j = 0; j < asArray.length; j++) {
                        ExcludeSpec right = asArray[j];
                        if (right != null && i != j) {
                            ExcludeSpec merged = intersections.tryIntersect(left, right);
                            if (merged != null) {
                                if (merged instanceof ExcludeNothing) {
                                    return merged;
                                }
                                left = merged;
                                asArray[i] = merged;
                                asArray[j] = null;
                                simplified = true;
                            }
                        }
                    }
                }
            }
            if (simplified) {
                result = Arrays.stream(asArray).filter(Objects::nonNull).collect(toSet());
            }
        }
        return Optimizations.optimizeCollection(this, result, delegate::allOf);
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

    private static class FlattenOperationResult {
        private static final FlattenOperationResult FAST_EXIT = new FlattenOperationResult(null, true);
        private final Set<ExcludeSpec> result;
        private final boolean fastExit;

        private FlattenOperationResult(Set<ExcludeSpec> result, boolean fastExit) {
            this.result = result;
            this.fastExit = fastExit;
        }

        public static FlattenOperationResult of(Set<ExcludeSpec> specs) {
            return new FlattenOperationResult(specs, false);
        }
    }
}
