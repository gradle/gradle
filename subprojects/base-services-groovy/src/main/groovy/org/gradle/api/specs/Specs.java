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

import java.util.Collection;
import java.util.List;

/**
 * Provides a number of {@link org.gradle.api.specs.Spec} implementations.
 */
public class Specs {

    public static final Spec<Object> SATISFIES_ALL = new Spec<Object>() {
        public boolean isSatisfiedBy(Object element) {
            return true;
        }
    };

    public static <T> Spec<T> satisfyAll() {
        return Cast.uncheckedCast(SATISFIES_ALL);
    }

    public static final Spec<Object> SATISFIES_NONE = new Spec<Object>() {
        public boolean isSatisfiedBy(Object element) {
            return false;
        }
    };

    public static <T> Spec<T> satisfyNone() {
        return Cast.uncheckedCast(SATISFIES_NONE);
    }

    //TODO SF rename for consistency with Actions.toAction
    public static <T> Spec<T> convertClosureToSpec(final Closure closure) {
        return new ClosureSpec<T>(closure);
    }

    public static <T> AndSpec<T> and(Spec<? super T>... specs) {
        return new AndSpec<T>(specs);
    }

    public static <T> AndSpec<T> and(Collection<? extends Spec<? super T>> specs) {
        return new AndSpec<T>(specs);
    }

    public static <T> OrSpec<T> or(Spec<? super T>... specs) {
        return new OrSpec<T>(specs);
    }

    public static <T> OrSpec<T> or(Collection<? extends Spec<? super T>> specs) {
        return new OrSpec<T>(specs);
    }

    public static <T> NotSpec<T> not(Spec<? super T> spec) {
        return new NotSpec<T>(spec);
    }

    public static <T> Spec<T> or(boolean defaultWhenNoSpecs, List<? extends Spec<? super T>> specs) {
        if (specs.isEmpty()) {
            return defaultWhenNoSpecs ? Specs.<T>satisfyAll() : Specs.<T>satisfyNone();
        }
        return new OrSpec<T>(specs);
    }
}
