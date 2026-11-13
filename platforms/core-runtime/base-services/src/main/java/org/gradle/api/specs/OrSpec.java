/*
 * Copyright 2007 the original author or authors.
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

/**
 * A {@link CompositeSpec} which requires any one of its specs to be true in order to evaluate to
 * true. As an exception, an {@code OrSpec} with no specs is satisfied by every object.
 * <p>
 * Uses lazy evaluation: member specs are evaluated in order, stopping at the first satisfied one.
 *
 * @param <T> The target type for this Spec
 * @since 0.7
 */
public class OrSpec<T> extends CompositeSpec<T> {
    /**
     * The shared {@code OrSpec} with no member specs, satisfied by every object.
     *
     * @see #empty()
     * @since 3.2
     */
    public static final OrSpec<?> EMPTY = new OrSpec<Object>();

    /**
     * Creates a spec with no member specs, which is satisfied by every object.
     *
     * @see #empty()
     * @since 3.0
     */
    public OrSpec() {
        super();
    }

    /**
     * Creates a spec with the given member specs, in order.
     *
     * @since 0.7
     */
    @SuppressWarnings("unchecked")
    public OrSpec(Spec<? super T>... specs) {
        super(specs);
    }

    /**
     * Creates a spec with the member specs of the given iterable, in iteration order.
     *
     * @since 1.3
     */
    public OrSpec(Iterable<? extends Spec<? super T>> specs) {
        super(specs);
    }

    @Override
    public boolean isSatisfiedBy(T object) {
        Spec<? super T>[] specs = getSpecsArray();
        if (specs.length == 0) {
            return true;
        }
        for (Spec<? super T> spec : specs) {
            if (spec.isSatisfiedBy(object)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an {@code OrSpec} whose member specs are this spec's, followed by the given ones.
     *
     * @since 3.2
     */
    @SuppressWarnings("unchecked")
    public OrSpec<T> or(Spec<? super T>... specs) {
        if (specs.length == 0) {
            return this;
        }
        Spec<? super T>[] thisSpecs = getSpecsArray();
        int thisLength = thisSpecs.length;
        if (thisLength == 0) {
            return new OrSpec<T>(specs);
        }
        Spec<? super T>[] combinedSpecs = uncheckedCast(ObjectArrays.newArray(Spec.class, thisLength + specs.length));
        System.arraycopy(thisSpecs, 0, combinedSpecs, 0, thisLength);
        System.arraycopy(specs, 0, combinedSpecs, thisLength, specs.length);
        return new OrSpec<T>(combinedSpecs);
    }

    /**
     * Returns the shared {@code OrSpec} with no member specs, which is satisfied by every object.
     * <p>
     * This is an exception to the usual rule that an empty disjunction is false.
     * Like every {@code OrSpec}, it is immutable,
     * so it is a starting point for building a disjunction.
     * Its vacuous truth is lost once {@code or(...)} is called on it with at least one spec.
     * An {@code OrSpec} that has it as a member is satisfied by every object.
     *
     * @since 3.2
     */
    public static <T> OrSpec<T> empty() {
        return uncheckedCast(EMPTY);
    }

}
