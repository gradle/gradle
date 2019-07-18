/*
 * Copyright 2009 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.specs.internal.ClosureSpec;
import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Provides a number of {@link org.gradle.api.specs.Spec} implementations.
 */
public class Specs {

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

    public static <T> Spec<T> convertClosureToSpec(final Closure closure) {
        return new ClosureSpec<T>(closure);
    }

    /**
     * Returns a spec that selects the intersection of those items selected by the given specs. Returns a spec that selects everything when no specs provided.
     */
    @SafeVarargs
    public static <T> Spec<T> intersect(Spec<? super T>... specs) {
        if (specs.length == 0) {
            return satisfyAll();
        }
        if (specs.length == 1) {
            return Cast.uncheckedCast(specs[0]);
        }
        return doIntersect(Arrays.<Spec<? super T>>asList(specs));
    }

    /**
     * Returns a spec that selects the intersection of those items selected by the given specs. Returns a spec that selects everything when no specs provided.
     */
    public static <T> Spec<T> intersect(Collection<? extends Spec<? super T>> specs) {
        if (specs.size() == 0) {
            return satisfyAll();
        }
        return doIntersect(specs);
    }

    private static <T> Spec<T> doIntersect(Collection<? extends Spec<? super T>> specs) {
        List<Spec<? super T>> filtered = new ArrayList<Spec<? super T>>(specs.size());
        for (Spec<? super T> spec : specs) {
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

    /**
     * Returns a spec that selects the union of those items selected by the provided spec. Selects everything when no specs provided.
     */
    @SafeVarargs
    public static <T> Spec<T> union(Spec<? super T>... specs) {
        if (specs.length == 0) {
            return satisfyAll();
        }
        if (specs.length == 1) {
            return Cast.uncheckedCast(specs[0]);
        }
        return doUnion(Arrays.<Spec<? super T>>asList(specs));
    }

    /**
     * Returns a spec that selects the union of those items selected by the provided spec. Selects everything when no specs provided.
     */
    public static <T> Spec<T> union(Collection<? extends Spec<? super T>> specs) {
        if (specs.size() == 0) {
            return satisfyAll();
        }
        return doUnion(specs);
    }

    private static <T> Spec<T> doUnion(Collection<? extends Spec<? super T>> specs) {
        List<Spec<? super T>> filtered = new ArrayList<Spec<? super T>>(specs.size());
        for (Spec<? super T> spec : specs) {
            if (spec == SATISFIES_ALL) {
                return satisfyAll();
            }
            if (spec != SATISFIES_NONE) {
                filtered.add(spec);
            }
        }
        if (filtered.size() == 0) {
            return satisfyNone();
        }
        if (filtered.size() == 1) {
            return Cast.uncheckedCast(filtered.get(0));
        }

        return new OrSpec<T>(filtered);
    }

    /**
     * Returns a spec that selects everything that is not selected by the given spec.
     */
    public static <T> Spec<T> negate(Spec<? super T> spec) {
        if (spec == SATISFIES_ALL) {
            return satisfyNone();
        }
        if (spec == SATISFIES_NONE) {
            return satisfyAll();
        }
        if (spec instanceof NotSpec) {
            NotSpec<? super T> notSpec = (NotSpec<? super T>) spec;
            return Cast.uncheckedCast(notSpec.getSourceSpec());
        }
        return new NotSpec<T>(spec);
    }

}
