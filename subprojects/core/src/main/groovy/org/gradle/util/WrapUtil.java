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
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.internal.reflect.DirectInstantiator;

import java.util.*;

/**
 * Common methods to wrap objects in generic collections.
 *
 */
public class WrapUtil {
    /**
     * Wraps the given items in a mutable unordered set.
     */
    public static <T> Set<T> toSet(T... items) {
        Set<T> coll = new HashSet<T>();
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable domain object set.
     */
    public static <T> DomainObjectSet<T> toDomainObjectSet(Class<T> type, T... items) {
        return new DefaultDomainObjectSet<T>(type, toSet(items));
    }

    /**
     * Wraps the given items in a named domain object set.
     */
    public static <T extends Named> NamedDomainObjectSet<T> toNamedDomainObjectSet(Class<T> type, T... items) {
        DefaultNamedDomainObjectSet<T> domainObjectSet = new DefaultNamedDomainObjectSet<T>(type, DirectInstantiator.INSTANCE);
        CollectionUtils.addAll(domainObjectSet, items);
        return domainObjectSet;
    }

    /**
     * Wraps the given items in a mutable ordered set.
     */
    public static <T> Set<T> toLinkedSet(T... items) {
        Set<T> coll = new LinkedHashSet<T>();
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable sorted set.
     */
    public static <T> SortedSet<T> toSortedSet(T... items) {
        SortedSet<T> coll = new TreeSet<T>();
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable sorted set using the given comparator.
     */
    public static <T> SortedSet<T> toSortedSet(Comparator<T> comp, T... items) {
        SortedSet<T> coll = new TreeSet<T>(comp);
        Collections.addAll(coll, items);
        return coll;
    }

    /**
     * Wraps the given items in a mutable list.
     */
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

    /**
     * Wraps the given key and value in a mutable sorted map.
     */
    public static <K, V> SortedMap<K, V> toSortedMap(K key, V value) {
        SortedMap<K, V> map = new TreeMap<K, V>();
        map.put(key, value);
        return map;
    }

    /**
     * Wraps the given key and value in a mutable ordered map.
     */
    public static <K, V> Map<K, V> toLinkedMap(K key, V value) {
        Map<K, V> map = new LinkedHashMap<K, V>();
        map.put(key, value);
        return map;
    }

    /**
     * Wraps the given key and value in a mutable properties instance.
     */
    public static Properties toProperties(String key, String value) {
        Properties props = new Properties();
        props.setProperty(key, value);
        return props;
    }

    public static <T> T[] toArray(T... items) {
        return items;
    }
    
    public static <T> Set<T> asSet(Collection<T> c) {
        return new LinkedHashSet<T>(c);
    }
}
