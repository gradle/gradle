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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides a number of {@link Spec} implementations.
 *
 * @author Hans Dockter
 */
public class Specs {
    public static final Spec SATISFIES_ALL = new Spec() {
        public boolean isSatisfiedBy(Object dependency) {
            return true;
        }
    };

    public static <T> Spec<T> satisfyAll() {
        return new Spec<T>() {
            public boolean isSatisfiedBy(T element) {
                return true;
            }
        };
    }

    public static <T> Spec<T> satisfyNone() {
        return new Spec<T>() {
            public boolean isSatisfiedBy(T element) {
                return false;
            }
        };
    }

    public static <T> Spec<T> convertClosureToSpec(final Closure cl) {
        return new Spec<T>() {
            public boolean isSatisfiedBy(T element) {
                Object value = cl.call(element);
                return value == null ? false : ((Boolean) value).booleanValue();
            }
        };
    }

    public static <T> Set<T> filterIterable(Iterable<? extends T> iterable, Spec<? super T> spec) {
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
