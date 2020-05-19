/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultDomainObjectSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Common methods to wrap objects in generic collections.
 */
public class WrapUtil {
    /**
     * Wraps the given items in a mutable unordered set.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Set<T> toSet(T... items) {
        Set<T> coll = new HashSet<T>();
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable domain object set.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> DomainObjectSet<T> toDomainObjectSet(Class<T> type, T... items) {
        DefaultDomainObjectSet<T> set = new DefaultDomainObjectSet<T>(type, CollectionCallbackActionDecorator.NOOP);
        set.addAll(Arrays.asList(items));
        return set;
    }

    /**
     * Wraps the given items in a mutable ordered set.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Set<T> toLinkedSet(T... items) {
        Set<T> coll = new LinkedHashSet<T>();
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable sorted set.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> SortedSet<T> toSortedSet(T... items) {
        SortedSet<T> coll = new TreeSet<T>();
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable sorted set using the given comparator.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> SortedSet<T> toSortedSet(Comparator<T> comp, T... items) {
        SortedSet<T> coll = new TreeSet<T>(comp);
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable list.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> List<T> toList(T... items) {
        ArrayList<T> coll = new ArrayList<T>();
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable list.
     */
    public static <T> List<T> toList(Iterable<? extends T> items) {
        ArrayList<T> coll = new ArrayList<T>();
        for (T item : items) {
            coll.add(item);
        }
        return coll;
    }

    /**
     * Wraps the given key and value in a mutable unordered map.
     */
    public static <K, V> Map<K, V> toMap(K key, V value) {
        Map<K, V> map = new HashMap<K, V>();
        map.put(key, value);
        return map;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> T[] toArray(T... items) {
        return items;
    }

}
