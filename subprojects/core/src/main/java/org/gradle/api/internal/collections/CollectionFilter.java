/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.collections;

import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import javax.annotation.Nullable;

public class CollectionFilter<T> implements Spec<Object> {

    private final Class<? extends T> type;
    private final Spec<? super T> spec;

    public CollectionFilter(Class<T> type) {
        this(type, Specs.satisfyAll());
    }

    public CollectionFilter(Class<? extends T> type, Spec<? super T> spec) {
        this.type = type;
        this.spec = spec;
    }

    public Class<? extends T> getType() {
        return type;
    }

    @Nullable
    public T filter(@Nullable Object object) {
        if (!type.isInstance(object)) {
            return null;
        }

        T t = type.cast(object);
        if (spec.isSatisfiedBy(t)) {
            return t;
        } else {
            return null;
        }
    }

    public Action<Object> filtered(final Action<? super T> action) {
        return new Action<Object>() {
            @Override
            public void execute(Object o) {
                T t = filter(o);
                if (t != null) {
                    action.execute(t);
                }
            }
        };
    }

    @Override
    public boolean isSatisfiedBy(Object element) {
        return filter(element) != null;
    }

    public <S extends T> CollectionFilter<S> and(CollectionFilter<S> other) {
        return new CollectionFilter<>(other.type, Specs.intersect(spec, other.spec));
    }
}
