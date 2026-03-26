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
import org.gradle.internal.collect.PersistentMap;
import org.gradle.internal.collect.PersistentSet;

import java.util.function.BiFunction;
import java.util.function.Predicate;

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
        return simplify(ExcludeAllOf.class, one, two, (left, right) -> doUnion(PersistentSet.of(left, right)));
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return simplify(ExcludeAnyOf.class, one, two, (left, right) -> doIntersect(PersistentSet.of(left, right)));
    }

    // Simplifies (A ∪ ...) ∩ A = A
    // and  (A ∩ ...) ∪ A = A
    private static ExcludeSpec simplify(Class<? extends CompositeExclude> clazz, ExcludeSpec one, ExcludeSpec two, BiFunction<ExcludeSpec, ExcludeSpec, ExcludeSpec> orElse) {
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
    private static PersistentSet<ExcludeSpec> simplifySet(Class<? extends CompositeExclude> clazz, PersistentSet<ExcludeSpec> specs) {
        return specs.filter(left ->
            !clazz.isInstance(left)
                || specs.noneMatch(right -> componentsOf(left).contains(right))
        );
    }

    private static PersistentSet<ExcludeSpec> componentsOf(ExcludeSpec spec) {
        return ((CompositeExclude) spec).getComponents();
    }

    @Override
    public ExcludeSpec anyOf(PersistentSet<ExcludeSpec> specs) {
        return doUnion(specs);
    }

    @Override
    public ExcludeSpec allOf(PersistentSet<ExcludeSpec> specs) {
        return doIntersect(specs);
    }

    private ExcludeSpec doUnion(PersistentSet<ExcludeSpec> specs) {
        specs = simplifySet(ExcludeAllOf.class, specs);
        FlattenOperationResult flattened = flatten(ExcludeAnyOf.class, specs, ExcludeEverything.class::isInstance, ExcludeNothing.class::isInstance);
        if (flattened.fastExit) {
            return everything();
        }
        if (flattened.result.isEmpty()) {
            return nothing();
        }
        PersistentMap<UnionOf, PersistentSet<ExcludeSpec>> byType = flattened.result.groupBy(UnionOf::typeOf);
        PersistentSet<ModuleIdExclude> moduleIdExcludes = UnionOf.MODULE_ID.fromMap(byType);
        PersistentSet<ModuleIdSetExclude> moduleIdSetsExcludes = UnionOf.MODULE_ID_SET.fromMap(byType);
        PersistentSet<GroupExclude> groupExcludes = UnionOf.GROUP.fromMap(byType);
        PersistentSet<GroupSetExclude> groupSetExcludes = UnionOf.GROUP_SET.fromMap(byType);
        PersistentSet<ModuleExclude> moduleExcludes = UnionOf.MODULE.fromMap(byType);
        PersistentSet<ModuleSetExclude> moduleSetExcludes = UnionOf.MODULE_SET.fromMap(byType);
        PersistentSet<ExcludeSpec> other = UnionOf.NOT_JOINABLE.fromMap(byType);
        if (!moduleIdExcludes.isEmpty()) {
            // If there's more than one module id, merge them into a module id set
            if (moduleIdExcludes.size() > 1 || !moduleIdSetsExcludes.isEmpty()) {
                ModuleIdSetExclude excludeSpec = delegate.moduleIdSet(moduleIdExcludes.map(ModuleIdExclude::getModuleId));
                if (moduleIdSetsExcludes.isEmpty()) {
                    moduleIdSetsExcludes = PersistentSet.of(excludeSpec);
                } else {
                    moduleIdSetsExcludes = moduleIdSetsExcludes.plus(excludeSpec);
                }
                moduleIdExcludes = PersistentSet.of();
            }
        }
        if (!groupExcludes.isEmpty()) {
            // If there's more than a group, merge them into a group set
            if (groupExcludes.size() > 1 || !groupSetExcludes.isEmpty()) {
                GroupSetExclude excludeSpec = delegate.groupSet(groupExcludes.map(GroupExclude::getGroup));
                if (groupSetExcludes.isEmpty()) {
                    groupSetExcludes = PersistentSet.of(excludeSpec);
                } else {
                    groupSetExcludes = groupSetExcludes.plus(excludeSpec);
                }
                groupExcludes = PersistentSet.of();
            }
        }
        if (!moduleExcludes.isEmpty()) {
            // If there's more than one module, merge them into a module set
            if (moduleExcludes.size() > 1 || !moduleSetExcludes.isEmpty()) {
                ModuleSetExclude excludeSpec = delegate.moduleSet(moduleExcludes.map(ModuleExclude::getModule));
                if (moduleSetExcludes.isEmpty()) {
                    moduleSetExcludes = PersistentSet.of(excludeSpec);
                } else {
                    moduleSetExcludes = moduleSetExcludes.plus(excludeSpec);
                }
                moduleExcludes = PersistentSet.of();
            }
        }
        if (moduleIdSetsExcludes.size() > 1) {
            moduleIdSetsExcludes = PersistentSet.of(delegate.moduleIdSet(moduleIdSetsExcludes.flatMap(ModuleIdSetExclude::getModuleIds)));
        }
        if (groupSetExcludes.size() > 1) {
            groupSetExcludes = PersistentSet.of(delegate.groupSet(groupSetExcludes.flatMap(GroupSetExclude::getGroups)));
        }
        if (moduleSetExcludes.size() > 1) {
            moduleSetExcludes = PersistentSet.of(delegate.moduleSet(moduleSetExcludes.flatMap(ModuleSetExclude::getModules)));
        }

        PersistentSet<ExcludeSpec> elements = PersistentSet.<ExcludeSpec>of()
            .union(moduleIdExcludes)
            .union(groupExcludes)
            .union(moduleExcludes)
            .union(moduleIdSetsExcludes)
            .union(groupSetExcludes)
            .union(moduleSetExcludes)
            .union(other);

        elements = fixedPointOf(this::simplifyUnion, elements);
        return Optimizations.optimizeCollection(this, elements, delegate::anyOf);
    }

    @FunctionalInterface
    interface Simplification {
        PersistentSet<ExcludeSpec> apply(ExcludeSpec left, ExcludeSpec right, PersistentSet<ExcludeSpec> specs);
    }

    /**
     * Computes the fixed point of a simplification over a set of {@link ExcludeSpec} elements.
     * <p>
     * The supplied {@code function} takes two candidate elements and the current set of
     * elements, and may return either the unchanged set (no simplification possible) or a
     * new set where a simplification has been applied (for example, by replacing both inputs
     * with a simplified/merged element, or by removing redundant elements).
     * <p>
     * This method repeatedly applies the simplification until no further changes occur. In
     * other words, it searches for a set {@code S*} such that
     * {@code simplify(S*) = S*}, where {@code simplify} denotes one full iteration of trying
     * to simplify all pairs. This mirrors the notion of a fixed point of a function on sets:
     * given a set-transforming function {@code F}, a fixed point is a set {@code S} for which
     * {@code F(S) = S}. Here, {@code F} is the "one-pass attempt" to simplify the current set
     * by examining element pairs; we keep applying {@code F} until the set stops changing.
     * <p>
     * Implementation details:
     * <ul>
     * <li>The algorithm iterates over pairs ({@code left}, {@code right}) from the snapshot of the
     * current set. If the simplification function returns a new set instance, iteration restarts
     * on that updated set. If a full pass causes no change, the current set is the fixed point
     * and is returned.</li>
     * <li>Termination is guaranteed because each successful simplification must either reduce the
     * number of elements or replace elements with a canonical merged element, and we only loop
     * while changes occur. Once no change is produced, the set equals its image under the
     * one-pass simplification function.</li>
     * </ul>
     *
     * @param function the pairwise simplification to apply over the set
     * @param specs the initial set of elements to simplify
     * @return the fixed point set where another full simplification pass yields the same set
     */
    private static PersistentSet<ExcludeSpec> fixedPointOf(Simplification function, PersistentSet<ExcludeSpec> specs) {
        PersistentSet<ExcludeSpec> current = specs;
        while (current.size() > 1) {
            PersistentSet<ExcludeSpec> simplified = simplifyOnce(function, current);
            if (simplified == current) {
                break;
            }
            current = simplified;
        }
        return current;
    }

    private static PersistentSet<ExcludeSpec> simplifyOnce(Simplification function, PersistentSet<ExcludeSpec> specs) {
        for (ExcludeSpec left : specs) {
            for (ExcludeSpec right : specs) {
                if (left == right) {
                    continue;
                }
                PersistentSet<ExcludeSpec> simplified = function.apply(left, right, specs);
                if (simplified != specs) {
                    return simplified;
                }
            }
        }
        return specs;
    }

    private PersistentSet<ExcludeSpec> simplifyUnion(ExcludeSpec left, ExcludeSpec right, PersistentSet<ExcludeSpec> specs) {
        ExcludeSpec merged = unions.tryUnion(left, right);
        if (merged != null) {
            if (merged instanceof ExcludeEverything) {
                return PersistentSet.of(merged);
            }
            PersistentSet<ExcludeSpec> simplified = specs.minus(left).minus(right);
            if (merged instanceof ExcludeAnyOf) {
                // Flatten it to its members, since we are building a union
                return simplified
                    .union(((ExcludeAnyOf) merged).getComponents());
            }
            return simplified
                .plus(merged);
        }
        return specs;
    }

    /**
     * Flattens a collection of elements that are going to be joined or intersected. There
     * are 3 possible outcomes:
     * - Null means that the fast exit condition was reached, meaning that the caller knows it's not worth computing more
     * - empty list meaning that an easy simplification was reached, and we directly know the result
     * - flattened unions/intersections
     */
    private static <T extends ExcludeSpec> FlattenOperationResult flatten(Class<T> flattenType, PersistentSet<ExcludeSpec> specs, Predicate<ExcludeSpec> fastExit, Predicate<ExcludeSpec> ignoreSpec) {
        boolean filtered = false;
        boolean flatten = false;
        for (ExcludeSpec spec : specs) {
            if (fastExit.test(spec)) {
                return FlattenOperationResult.FAST_EXIT;
            }
            if (ignoreSpec.test(spec)) {
                filtered = true;
            } else if (flattenType.isInstance(spec)) {
                flatten = true;
            }
        }
        if (!filtered && !flatten) {
            return FlattenOperationResult.of(specs);
        }
        if (filtered && !flatten) {
            return filterOnly(specs, ignoreSpec);
        }
        // slowest path
        return expensiveFlatten(flattenType, maybeFilter(specs, ignoreSpec, filtered));
    }

    private static FlattenOperationResult filterOnly(PersistentSet<ExcludeSpec> specs, Predicate<ExcludeSpec> ignoreSpec) {
        return FlattenOperationResult.of(specs.filter(e -> !ignoreSpec.test(e)));
    }

    private static PersistentSet<ExcludeSpec> maybeFilter(PersistentSet<ExcludeSpec> specs, Predicate<ExcludeSpec> ignoreSpec, boolean filtered) {
        PersistentSet<ExcludeSpec> stream = specs;
        if (filtered) {
            stream = stream.filter(e -> !ignoreSpec.test(e));
        }
        return stream;
    }

    private static <T extends ExcludeSpec> FlattenOperationResult expensiveFlatten(Class<T> flattenType, PersistentSet<ExcludeSpec> specs) {
        return FlattenOperationResult.of(
            specs.flatMap(e -> {
                if (flattenType.isInstance(e)) {
                    CompositeExclude compositeExclude = (CompositeExclude) e;
                    return compositeExclude.getComponents();
                }
                return PersistentSet.of(e);
            })
        );
    }

    private ExcludeSpec doIntersect(PersistentSet<ExcludeSpec> specs) {
        specs = simplifySet(ExcludeAnyOf.class, specs);
        FlattenOperationResult flattened = flatten(ExcludeAllOf.class, specs, ExcludeNothing.class::isInstance, ExcludeEverything.class::isInstance);
        if (flattened.fastExit) {
            return nothing();
        }
        PersistentSet<ExcludeSpec> result = flattened.result;
        if (result.isEmpty()) {
            return everything();
        }
        result = fixedPointOf(this::simplifyIntersect, result);
        return Optimizations.optimizeCollection(this, result, delegate::allOf);
    }

    private PersistentSet<ExcludeSpec> simplifyIntersect(ExcludeSpec left, ExcludeSpec right, PersistentSet<ExcludeSpec> specs) {
        ExcludeSpec merged = intersections.tryIntersect(left, right);
        if (merged != null) {
            if (merged instanceof ExcludeNothing) {
                return PersistentSet.of(merged);
            }
            return specs
                .minus(left)
                .minus(right)
                .plus(merged);
        }
        return specs;
    }

    private enum UnionOf {
        MODULE_ID(ModuleIdExclude.class),
        GROUP(GroupExclude.class),
        MODULE(ModuleExclude.class),
        MODULE_ID_SET(ModuleIdSetExclude.class),
        GROUP_SET(GroupSetExclude.class),
        MODULE_SET(ModuleSetExclude.class),
        NOT_JOINABLE(ExcludeSpec.class);

        private final Class<? extends ExcludeSpec> excludeClass;

        UnionOf(Class<? extends ExcludeSpec> excludeClass) {
            this.excludeClass = excludeClass;
        }

        public <T extends ExcludeSpec> PersistentSet<T> fromMap(PersistentMap<UnionOf, PersistentSet<ExcludeSpec>> from) {
            return Cast.uncheckedCast(from.getOrDefault(this, PersistentSet.of()));
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
        private final PersistentSet<ExcludeSpec> result;
        private final boolean fastExit;

        private FlattenOperationResult(PersistentSet<ExcludeSpec> result, boolean fastExit) {
            this.result = result;
            this.fastExit = fastExit;
        }

        public static FlattenOperationResult of(PersistentSet<ExcludeSpec> specs) {
            return new FlattenOperationResult(specs, false);
        }
    }
}
