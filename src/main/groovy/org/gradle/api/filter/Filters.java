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
package org.gradle.api.filter;

import org.gradle.api.dependencies.Dependency;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Hans Dockter
 */
public class Filters {
    public static final FilterSpec NO_FILTER = new FilterSpec() {
        public boolean isSatisfiedBy(Object dependency) {
            return true;
        }
    };

    public static <T> FilterSpec<T> noFilter() {
        return (FilterSpec<T>) NO_FILTER;
    }

    public static <T> List<T> filterIterable(Iterable<T> iterable, FilterSpec<T> filter) {
        List<T> result = new ArrayList<T>();
        for (T t : iterable) {
            if (filter.isSatisfiedBy(t)) {
                result.add(t);
            }
        }
        return result;
    }

    public static <T> AndSpec<T> and(FilterSpec<T>... specs) {
        return new AndSpec<T>(specs);  
    }

    public static <T> OrSpec<T> or(FilterSpec<T>... specs) {
        return new OrSpec<T>(specs);
    }

    public static <T> NotSpec<T> not(FilterSpec<T> spec) {
        return new NotSpec<T>(spec);  
    }
}
