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

import com.google.common.collect.Lists;
import org.gradle.api.specs.Spec;
import org.gradle.api.Transformer;

import java.util.*;

public abstract class CollectionUtils {

    public static <T> T findFirst(Iterable<T> source, Spec<? super T> filter) {
        for (T item : source) {
            if (filter.isSatisfiedBy(item)) {
                return item;
            }
        }

        return null;
    }
    
    public static <T> Set<T> filter(Set<T> set, Spec<? super T> filter) {
        return filter(set, new LinkedHashSet<T>(), filter);
    }

    public static <T> List<T> filter(List<T> list, Spec<? super T> filter) {
        return filter(list, new LinkedList<T>(), filter);
    }

    public static <T, C extends Collection<T>> C filter(Iterable<T> source, C destination, Spec<? super T> filter) {
        for (T item : source) {
             if (filter.isSatisfiedBy(item)) {
                 destination.add(item);
             }
         }
         return destination;
    }

    public static <R, I> List<R> collect(List<? extends I> list, Transformer<R, I> transformer) {
        return collect(list, new ArrayList<R>(list.size()), transformer);
    }

    public static <R, I> Set<R> collect(Set<? extends I> set, Transformer<R, I> transformer) {
        return collect(set, new HashSet<R>(), transformer);
    }

    public static <R, I, C extends Collection<R>> C collect(Iterable<? extends I> source, C destination, Transformer<R, I> transformer) {
        for (I item : source) {
            destination.add(transformer.transform(item));
        }
        return destination;
    }

    public static List<String> toStringList(Iterable<?> iterable) {
        List<String> result = Lists.newArrayList();
        for (Object elem : iterable) {
            result.add(elem.toString());
        }
        return result;
    }
}