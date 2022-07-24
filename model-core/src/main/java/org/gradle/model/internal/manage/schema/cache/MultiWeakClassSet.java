/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.cache;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.internal.Cast;

import java.lang.ref.WeakReference;
import java.util.List;

class MultiWeakClassSet extends WeakClassSet {

    private static final Function<Class<?>, WeakReference<Class<?>>> TO_WEAK_REF = new Function<Class<?>, WeakReference<Class<?>>>() {
        @Override
        public WeakReference<Class<?>> apply(Class<?> input) {
            return new WeakReference<Class<?>>(input);
        }
    };
    private static final Function<WeakReference<Class<?>>, Object> UNPACK_REF = new Function<WeakReference<Class<?>>, Object>() {
        @Override
        public Object apply(WeakReference<Class<?>> input) {
            return input.get();
        }
    };

    private final List<WeakReference<Class<?>>> references;
    private final int hash;

    MultiWeakClassSet(List<Class<?>> classes) {
        this.references = Lists.newArrayList(Iterables.transform(classes, TO_WEAK_REF));
        this.hash = classes.hashCode();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MultiWeakClassSet) {
            MultiWeakClassSet other = Cast.uncheckedCast(obj);
            if (other.references.size() == references.size()) {
                return Iterables.elementsEqual(
                        Iterables.transform(other.references, UNPACK_REF),
                        Iterables.transform(references, UNPACK_REF)
                );
            }
        }

        return false;
    }

    @Override
    boolean isCollected() {
        for (WeakReference<Class<?>> reference : references) {
            if (reference.get() == null) {
                return true;
            }
        }

        return false;
    }

}
