/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.base.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.gradle.api.specs.Spec;
import org.gradle.testing.base.MatrixContainer;
import org.gradle.testing.base.MatrixCoordinates;
import org.gradle.testing.base.MatrixDimensions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class DefaultMatrixDimensions<V extends MatrixContainer.MatrixValue> implements MatrixDimensions {
    private SetMultimap<String, Object> dimensions = LinkedHashMultimap.create();

    private Set<Spec<? super MatrixCoordinates>> includes = new HashSet<>();
    private Set<Spec<? super MatrixCoordinates>> excludes = new HashSet<>();
    private Function<MatrixCoordinatesInternal, V> valueFactory;
    private Set<V> targets;

    public DefaultMatrixDimensions(Function<MatrixCoordinatesInternal, V> valueFactory) {
        this.valueFactory = valueFactory;
    }

    private void checkUnused() {
        if (targets != null) {
            throw new IllegalStateException("Cannot configure dimensions after they have been used.");
        }
    }

    @Override
    public void include(Spec<? super MatrixCoordinates> spec) {
        checkUnused();
        includes.add(spec);
    }

    @Override
    public void exclude(Spec<? super MatrixCoordinates> spec) {
        checkUnused();
        excludes.add(spec);
    }

    @Override
    public <T> void dimension(String dimension, Iterable<T> values) {
        checkUnused();
        dimensions.putAll(dimension, values);
    }

    private static final class DimEntry {
        String dimension;
        Object value;

        DimEntry(String dimension, Object value) {
            this.dimension = dimension;
            this.value = value;
        }
    }

    public Set<V> getMatrixValues() {
        if (targets != null) {
            return targets;
        }
        Set<List<DimEntry>> product = Sets.cartesianProduct(
            Multimaps.asMap(dimensions).entrySet().stream()
                // Map each dimension set into a list of entries (keeping iteration order)
                .map(e -> e.getValue().stream().map(v -> new DimEntry(e.getKey(), v)).collect(ImmutableSet.toImmutableSet()))
                // Collect each dimension (List<DimEntry>) into a list
                .collect(ImmutableList.toImmutableList())
        );

        // TODO optimize by keeping the product list sometimes instead of doing a copy
        // only needed if the product is too large to fit in a reasonable amount of memory
        this.targets = product.stream()
            .map(entries -> new DefaultMatrixCoordinates(
                entries.stream()
                    .collect(ImmutableMap.toImmutableMap(e -> e.dimension, e -> e.value))
            ))
            .filter(target -> includes.stream().allMatch(s -> s.isSatisfiedBy(target)))
            .filter(target -> excludes.stream().noneMatch(s -> s.isSatisfiedBy(target)))
            .map(valueFactory)
            .collect(ImmutableSet.toImmutableSet());

        // Wipe out references so it can be GC'd
        dimensions = null;
        includes = null;
        excludes = null;

        return this.targets;
    }
}
