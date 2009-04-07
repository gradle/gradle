/*
 * Copyright 2007-2009 the original author or authors.
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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class Specs {
    public static final Spec SATISFIES_ALL = new Spec() {
        public boolean isSatisfiedBy(Object dependency) {
            return true;
        }
    };

    public static <T> Spec<T> satisfyAll() {
        return (Spec<T>) SATISFIES_ALL;
    }

    public static <T> Set<T> filterIterable(Iterable<T> iterable, Spec<T> spec) {
        Set<T> result = new LinkedHashSet<T>();
        for (T t : iterable) {
            if (spec.isSatisfiedBy(t)) {
                result.add(t);
            }
        }
        return result;
    }

    public static <T> AndSpec<T> and(Spec<T>... specs) {
        return new AndSpec<T>(specs);  
    }

    public static <T> OrSpec<T> or(Spec<T>... specs) {
        return new OrSpec<T>(specs);
    }

    public static <T> NotSpec<T> not(Spec<T> spec) {
        return new NotSpec<T>(spec);  
    }
}
