/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.integtests.tooling.fixture;

import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This interface is copied and trimmed from org.gradle.api.specs.Spec because older versions of Tooling API have issues with
 * the original due to different versions of classes loaded.
 *
 */
interface Spec<T> {
    boolean isSatisfiedBy(T element);
}

/**
 * This class is copied and trimmed from org.gradle.api.specs.Specs because older versions of Tooling API have issues with
 * the original due to different versions of classes loaded.
 */
class Specs {

    public static final Spec<Object> SATISFIES_ALL = new Spec<Object>() {
        @Override
        public boolean isSatisfiedBy(Object element) {
            return true;
        }
    };

    public static <T> Spec<T> satisfyAll() {
        return Cast.uncheckedCast(SATISFIES_ALL);
    }

    public static final Spec<Object> SATISFIES_NONE = new Spec<Object>() {
        @Override
        public boolean isSatisfiedBy(Object element) {
            return false;
        }
    };

    public static <T> Spec<T> satisfyNone() {
        return Cast.uncheckedCast(SATISFIES_NONE);
    }

    /**
     * Returns a spec that selects the intersection of those items selected by the given specs. Returns a spec that selects everything when no specs provided.
     */
    public static <T> Spec<T> intersect(Collection<? extends Spec<T>> specs) {
        if (specs.size() == 0) {
            return satisfyAll();
        }
        return doIntersect(specs);
    }

    private static <T> Spec<T> doIntersect(Collection<? extends Spec<T>> specs) {
        List<Spec<T>> filtered = new ArrayList<Spec<T>>(specs.size());
        for (Spec<T> spec : specs) {
            if (spec == SATISFIES_NONE) {
                return satisfyNone();
            }
            if (spec != SATISFIES_ALL) {
                filtered.add(spec);
            }
        }
        if (filtered.size() == 0) {
            return satisfyAll();
        }
        if (filtered.size() == 1) {
            return Cast.uncheckedCast(filtered.get(0));
        }
        return new AndSpec<T>(filtered);
    }
}

class AndSpec<T> implements Spec<T> {

    private final List<Spec<? super T>> specs;

    public AndSpec(List<Spec<T>> specs) {
        this.specs = new ArrayList<Spec<? super T>>(specs);
    }

    @Override
    public boolean isSatisfiedBy(T object) {
        for (Spec<? super T> spec : specs) {
            if (!spec.isSatisfiedBy(object)) {
                return false;
            }
        }
        return true;
    }
}
