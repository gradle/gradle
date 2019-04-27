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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.CompositeExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAllOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
        Set<ModuleIdExclude> simpleExcludes = null;
        List<ModuleSetExclude> moduleSets = null;
        Set<ExcludeSpec> flattened = flatten(ExcludeAnyOf.class, specs, ExcludeEverything.class::isInstance, ExcludeNothing.class::isInstance);
        if (flattened == null) {
            return everything();
        }
        for (Iterator<ExcludeSpec> it = flattened.iterator(); it.hasNext(); ) {
            ExcludeSpec spec = it.next();
            if (spec instanceof ModuleIdExclude) {
                if (simpleExcludes == null) {
                    simpleExcludes = Sets.newHashSetWithExpectedSize(specs.size());
                }
                simpleExcludes.add((ModuleIdExclude) spec);
                it.remove();
            }
            // will allow merging module sets into a single one
            if (spec instanceof ModuleSetExclude) {
                if (moduleSets == null) {
                    moduleSets = Lists.newArrayList();
                }
                moduleSets.add((ModuleSetExclude) spec);
                it.remove();

            }
        }
        // merge all single module id into an id set
        if (simpleExcludes != null) {
            if (simpleExcludes.size() > 1 || moduleSets != null) {
                ModuleSetExclude e = (ModuleSetExclude) delegate.moduleSet(simpleExcludes.stream().map(ModuleIdExclude::getModuleId).collect(Collectors.toSet()));
                if (moduleSets != null) {
                    moduleSets.add(e);
                } else {
                    flattened.add(e);
                }
            } else {
                flattened.add(simpleExcludes.iterator().next());
            }
        }
        if (moduleSets != null) {
            if (moduleSets.size() == 1) {
                flattened.add(moduleSets.get(0));
            } else {
                // merge all module sets
                flattened.add(delegate.moduleSet(moduleSets.stream().flatMap(e -> e.getModuleIds().stream()).collect(Collectors.toSet())));
            }
        }
        if (flattened.isEmpty()) {
            return nothing();
        }
        return Optimizations.optimizeList(this, ImmutableList.copyOf(flattened), delegate::anyOf);
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
}
