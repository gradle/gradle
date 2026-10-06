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

import com.google.common.collect.ObjectArrays;
import groovy.lang.Closure;
import org.gradle.api.specs.internal.ClosureSpec;
import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

/**
 * A {@link org.gradle.api.specs.CompositeSpec} which requires all its specs to be true in order to evaluate to true.
 * <p>
 * Uses lazy evaluation: member specs are evaluated in order, stopping at the first unsatisfied one.
 *
 * @param <T> The target type for this Spec
 * @since 0.7
 */
public class AndSpec<T> extends CompositeSpec<T> {
    /**
     * The shared {@code AndSpec} with no member specs, satisfied by every object.
     *
     * @see #empty()
     * @since 3.1
     */
    public static final AndSpec<?> EMPTY = new AndSpec<>();

    /**
     * Creates a spec with no member specs, which is satisfied by every object.
     *
     * @see #empty()
     * @since 3.0
     */
    public AndSpec() {
        super();
    }

    /**
     * Creates a spec with the given member specs, in order.
     *
     * @since 0.7
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public AndSpec(Spec<? super T>... specs) {
        super(specs);
    }

    /**
     * Creates a spec with the member specs of the given iterable, in iteration order.
     *
     * @since 0.9
     */
    public AndSpec(Iterable<? extends Spec<? super T>> specs) {
        super(specs);
    }

    @Override
    public boolean isSatisfiedBy(T object) {
        return findUnsatisfiedSpec(object) == null;
    }

    /**
     * Finds the first {@link Spec} that is not satisfied by the object.
     *
     * @param object to check specs against
     * @return the first unsatisfied member spec, or {@code null} if all of them are satisfied
     *
     * @since 7.6
     */
    @Nullable
    public Spec<? super T> findUnsatisfiedSpec(T object) {
        Spec<? super T>[] specs = getSpecsArray();
        for (Spec<? super T> spec : specs) {
            if (!spec.isSatisfiedBy(object)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * Returns an {@code AndSpec} whose member specs are this spec's, followed by the given ones.
     *
     * @since 0.9
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AndSpec<T> and(Spec<? super T>... specs) {
        if (specs.length == 0) {
            return this;
        }
        Spec<? super T>[] thisSpecs = getSpecsArray();
        int thisLength = thisSpecs.length;
        if (thisLength == 0) {
            return new AndSpec<T>(specs);
        }
        Spec<? super T>[] combinedSpecs = uncheckedCast(ObjectArrays.newArray(Spec.class, thisLength + specs.length));
        System.arraycopy(thisSpecs, 0, combinedSpecs, 0, thisLength);
        System.arraycopy(specs, 0, combinedSpecs, thisLength, specs.length);
        return new AndSpec<T>(combinedSpecs);
    }

    /**
     * Returns an {@code AndSpec} whose member specs are this spec's, followed by the given one.
     *
     * @since 4.3
     */
    public AndSpec<T> and(Spec<? super T> spec) {
        return and(Cast.<Spec<? super T>[]>uncheckedNonnullCast(new Spec<?>[]{spec}));
    }

    /**
     * Returns an {@code AndSpec} whose member specs are this spec's,
     * followed by the closure as a spec.
     *
     * @see Specs#convertClosureToSpec(Closure)
     * @since 0.9
     */
    @SuppressWarnings("rawtypes")
    public AndSpec<T> and(Closure spec) {
        return and(new ClosureSpec<>(spec));
    }

    /**
     * Returns the shared {@code AndSpec} with no member specs,
     * which is vacuously satisfied by every object.
     * <p>
     * Like every {@code AndSpec}, it is immutable,
     * so it is a starting point for building a conjunction.
     * As a member, it does not change which objects satisfy a conjunction.
     *
     * @since 3.1
     */
    public static <T> AndSpec<T> empty() {
        return uncheckedCast(EMPTY);
    }

}
