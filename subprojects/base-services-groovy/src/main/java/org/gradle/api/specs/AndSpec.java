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
import org.gradle.api.Incubating;
import org.gradle.api.specs.internal.ClosureSpec;

/**
 * A {@link org.gradle.api.specs.CompositeSpec} which requires all its specs to be true in order to evaluate to true.
 * Uses lazy evaluation.
 *
 * @param <T> The target type for this Spec
 */
public class AndSpec<T> extends CompositeSpec<T> {
    public static final AndSpec<?> EMPTY = new AndSpec<Object>();

    public AndSpec() {
        super();
    }

    public AndSpec(Spec<? super T>... specs) {
        super(specs);
    }

    public AndSpec(Iterable<? extends Spec<? super T>> specs) {
        super(specs);
    }

    @Override
    public boolean isSatisfiedBy(T object) {
        Spec<? super T>[] specs = getSpecsArray();
        for (Spec<? super T> spec : specs) {
            if (!spec.isSatisfiedBy(object)) {
                return false;
            }
        }
        return true;
    }

    public AndSpec<T> and(Spec<? super T>... specs) {
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
     * Typed and() method for a single {@link Spec}.
     *
     * @since 4.3
     */
    @Incubating
    @SuppressWarnings("unchecked")
    public AndSpec<T> and(Spec<? super T> spec) {
        return and(new Spec[]{spec});
    }

    @SuppressWarnings("unchecked")
    public AndSpec<T> and(Closure spec) {
        return and(new ClosureSpec<T>(spec));
    }

    public static <T> AndSpec<T> empty() {
        return uncheckedCast(EMPTY);
    }

}
