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
import org.gradle.util.DeprecationLogger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides a number of {@link org.gradle.api.specs.Spec} implementations.
 */
public class Specs {

    /*
        Note: This should be in baseServicesGroovy, but it needs the DeprecationLogger which needs commons-lang
              It as
     */
    public static final Spec<Object> SATISFIES_ALL = new Spec<Object>() {
        public boolean isSatisfiedBy(Object element) {
            return true;
        }
    };

    public static <T> Spec<T> satisfyAll() {
        return (Spec<T>)SATISFIES_ALL;
    }

    public static final Spec<Object> SATISFIES_NONE = new Spec<Object>() {
        public boolean isSatisfiedBy(Object element) {
            return false;
        }
    };
    
    public static <T> Spec<T> satisfyNone() {
        return (Spec<T>)SATISFIES_NONE;
    }

    public static <T> Spec<T> convertClosureToSpec(final Closure closure) {
        return new ClosureSpec<T>(closure);
    }

    public static <T> Set<T> filterIterable(Iterable<? extends T> iterable, Spec<? super T> spec) {
        DeprecationLogger.nagUserOfReplacedMethod("Specs.filterIterable", "CollectionUtils.filter");
        Set<T> result = new LinkedHashSet<T>();
        for (T t : iterable) {
            if (spec.isSatisfiedBy(t)) {
                result.add(t);
            }
        }
        return result;
    }

    public static <T> AndSpec<T> and(Spec<? super T>... specs) {
        return new AndSpec<T>(specs);  
    }

    public static <T> OrSpec<T> or(Spec<? super T>... specs) {
        return new OrSpec<T>(specs);
    }

    public static <T> NotSpec<T> not(Spec<? super T> spec) {
        return new NotSpec<T>(spec);  
    }

    public static <T> Spec<T> or(final boolean defaultWhenNoSpecs, List<? extends Spec<? super T>> specs) {
        if (specs.isEmpty()) {
            return new Spec<T>() {
                public boolean isSatisfiedBy(T element) {
                    return defaultWhenNoSpecs;
                }
            };
        }
        return new OrSpec<T>(specs.toArray(new Spec[specs.size()]));
    }
}
