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
package org.gradle.util;

import org.gradle.api.specs.Spec;
import org.gradle.api.Transformer;

import java.util.List;
import java.util.LinkedList;
import java.util.Collection;

abstract public class CollectionUtils {

    public static <T> List<T> filterList(List<T> list, Spec<T> filter) {
        return doFilter(list, new LinkedList<T>(), filter);
    }

    private static <T, C extends Collection<T>> C doFilter(Collection<T> source, C destination, Spec<T> filter) {
        for (T item : source) {
             if (filter.isSatisfiedBy(item)) {
                 destination.add(item);
             }
         }
         return destination;
    }

    public static <R, I> List<R> collectList(List<I> list, Transformer<R, I> transformer) {
        return doCollect(list, new LinkedList<R>(), transformer);
    }

    private static <R, I, C extends Collection<R>> C doCollect(Collection<I> source, C destination, Transformer<R, I> transformer) {
        for (I item : source) {
            destination.add(transformer.transform(item));
        }
        return destination;
    }

}