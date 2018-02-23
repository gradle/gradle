/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.specs;

import com.google.common.collect.Iterators;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link org.gradle.api.specs.Spec} which aggregates a sequence of other {@code Spec} instances.
 *
 * @param <T> The target type for this Spec
 */
public abstract class CompositeSpec<T> implements Spec<T> {
    private static final Spec<?>[] EMPTY = new Spec[0];

    private final Spec<? super T>[] specs;

    protected CompositeSpec() {
        this.specs = uncheckedCast(EMPTY);
    }

    protected CompositeSpec(Spec<? super T>... specs) {
        if (specs.length == 0) {
            this.specs = uncheckedCast(EMPTY);
        } else {
            this.specs = specs.clone();
        }
    }

    protected CompositeSpec(Iterable<? extends Spec<? super T>> specs) {
        if (specs instanceof Collection) {
            Collection<Spec<? super T>> specCollection = uncheckedCast(specs);
            if (specCollection.isEmpty()) {
                this.specs = uncheckedCast(EMPTY);
            } else {
                this.specs = uncheckedCast(specCollection.toArray(EMPTY));
            }
        } else {
            Iterator<? extends Spec<? super T>> iterator = specs.iterator();
            if (!iterator.hasNext()) {
                this.specs = uncheckedCast(EMPTY);
            } else {
                this.specs = uncheckedCast(Iterators.toArray(iterator, Spec.class));
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    // Not public. Evaluation of these specs is a major hot spot for large builds, so use an array for iteration
    Spec<? super T>[] getSpecsArray() {
        return specs;
    }

    public List<Spec<? super T>> getSpecs() {
        return Collections.unmodifiableList(Arrays.asList(specs));
    }

    public boolean isEmpty() {
        return specs.length == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CompositeSpec that = (CompositeSpec) o;
        return Arrays.equals(specs, that.specs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(specs);
    }
}
