/*
 * Copyright 2007-2008 the original author or authors.
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
 * true. Uses lazy evaluation.
 *
 * @param <T> The target type for this Spec
 */
public class OrSpec<T> extends CompositeSpec<T> {
    public static final OrSpec<?> EMPTY = new OrSpec<Object>();

    public OrSpec() {
        super();
    }

    public OrSpec(Spec<? super T>... specs) {
        super(specs);
    }

    public OrSpec(Iterable<? extends Spec<? super T>> specs) {
        super(specs);
    }

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

    public static <T> OrSpec<T> empty() {
        return uncheckedCast(EMPTY);
    }

    @Override
    public int hashCode() {
        return 13 * super.hashCode();
    }
}
