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

import org.gradle.api.Transformer;
import org.gradle.api.internal.Transformers;
import org.gradle.api.specs.Spec;

import java.lang.reflect.Array;
import java.util.*;

import static org.gradle.api.internal.Cast.cast;

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

    public static <K, V> Map<K, V> filter(Map<K, V> map, Spec<Map.Entry<K, V>> filter) {
        return filter(map, new HashMap<K, V>(), filter);
    }

    public static <K, V> Map<K, V> filter(Map<K, V> map, Map<K, V> destination, Spec<Map.Entry<K, V>> filter) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (filter.isSatisfiedBy(entry)) {
                destination.put(entry.getKey(), entry.getValue());
            }
        }

        return destination;
    }

    public static <R, I> R[] collectArray(I[] list, Class<R> newType, Transformer<R, I> transformer) {
        return collectArray(list, (R[]) Array.newInstance(newType, list.length), transformer);
    }

    public static <R, I> R[] collectArray(I[] list, R[] destination, Transformer<R, I> transformer) {
        assert list.length <= destination.length;
        for (int i = 0; i < list.length; ++i) {
            destination[i] = transformer.transform(list[i]);
        }
        return destination;
    }

    public static <R, I> List<R> collect(List<? extends I> list, Transformer<R, ? super I> transformer) {
        return collect(list, new ArrayList<R>(list.size()), transformer);
    }

    public static <R, I> List<R> collect(I[] list, Transformer<R, ? super I> transformer) {
        return collect(Arrays.asList(list), transformer);
    }

    public static <R, I> Set<R> collect(Set<? extends I> set, Transformer<R, ? super I> transformer) {
        return collect(set, new HashSet<R>(), transformer);
    }

    public static <R, I, C extends Collection<R>> C collect(Iterable<? extends I> source, C destination, Transformer<R, ? super I> transformer) {
        for (I item : source) {
            destination.add(transformer.transform(item));
        }
        return destination;
    }

    public static List<String> toStringList(Iterable<?> iterable) {
        return collect(iterable, new LinkedList<String>(), Transformers.asString());
    }

    /**
     * Recursively unpacks all the given things into a flat list.
     *
     * Nulls are not removed, they are left intact.
     *
     * @param things The things to flatten
     * @return A flattened list of the given things
     */
    public static List<?> flattenToList(Object... things) {
        return flattenToList(Object.class, things);
    }

    /**
     * Recursively unpacks all the given things into a flat list, ensuring they are of a certain type.
     *
     * Nulls are not removed, they are left intact.
     *
     * If a non null object cannot be cast to the target type, a ClassCastException will be thrown.
     *
     * @param things The things to flatten
     * @param <T> The target type in the flattened list
     * @return A flattened list of the given things
     */
    public static <T> List<? extends T> flattenToList(Class<T> type, Object... things) {
        if (things == null) {
            return Collections.singletonList(null);
        } else if (things.length == 0) {
            return Collections.emptyList();
        } else if (things.length == 1) {
            Object thing = things[0];

            if (thing == null) {
                return Collections.singletonList(null);
            }

            if (thing.getClass().isArray()) {
                Object[] thingArray = (Object[]) thing;
                List<T> list = new ArrayList<T>(thingArray.length);
                for (Object thingThing : thingArray) {
                    list.addAll(flattenToList(type, thingThing));
                }
                return list;
            }

            if (thing instanceof Iterable) {
                Iterable<?> iterableThing = (Iterable<?>) thing;
                List<T> list = new ArrayList<T>();
                for (Object thingThing : iterableThing) {
                    list.addAll(flattenToList(type, thingThing));
                }
                return list;
            }

            return Collections.singletonList(cast(type, thing));
        } else {
            List<T> list = new ArrayList<T>();
            for (Object thing : things) {
                list.addAll(flattenToList(type, thing));
            }
            return list;
        }
    }

    public static <T> List<T> toList(Iterable<? extends T> things) {
        if (things == null) {
            return new ArrayList<T>(0);
        }
        if (things instanceof List) {
            return (List<T>) things;
        }

        List<T> list = new ArrayList<T>();
        for (T thing : things) {
            list.add(thing);
        }
        return list;
    }

    public static <E> List<E> compact(List<E> list) {
        boolean foundAtLeastOneNull = false;
        List<E> compacted = null;
        int i = 0;

        for (E element : list) {
            if (element == null) {
                if (!foundAtLeastOneNull) {
                    compacted = new ArrayList<E>(list.size());
                    if (i > 0) {
                        compacted.addAll(list.subList(0, i));
                    }
                }
                foundAtLeastOneNull = true;
            } else if (foundAtLeastOneNull) {
                compacted.add(element);
            }
            ++i;
        }

        return foundAtLeastOneNull ? compacted : list;
    }

    public static <C extends Collection<String>> C stringize(Iterable<?> source, C destination) {
        return collect(source, destination, Transformers.asString());
    }

    public static List<String> stringize(List<?> source) {
        return stringize(source, new ArrayList<String>(source.size()));
    }

    public static <E> boolean replace(List<E> list, Spec<? super E> filter, Transformer<E, ? super E> transformer) {
        boolean replaced = false;
        int i = 0;
        for (E it : list) {
            if (filter.isSatisfiedBy(it)) {
                list.set(i, transformer.transform(it));
                replaced = true;
            }
            ++i;
        }
        return replaced;
    }

    public static <K, V> void collectMap(Map<K, V> destination, Iterable<? extends V> items, Transformer<? extends K, ? super V> keyGenerator) {
        for (V item : items) {
            destination.put(keyGenerator.transform(item), item);
        }
    }

    public static <K, V> Map<K, V> collectMap(Iterable<? extends V> items, Transformer<? extends K, ? super V> keyGenerator) {
        Map<K, V> map = new LinkedHashMap<K, V>();
        collectMap(map, items, keyGenerator);
        return map;
    }

    public static <T> boolean every(Iterable<T> things, Spec<? super T> predicate) {
        for (T thing : things) {
            if (!predicate.isSatisfiedBy(thing)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Utility for adding an iterable to a collection.
     *
     * @param t1 The collection to add to
     * @param t2 The iterable to add each item of to the collection
     * @param <T> The element type of t1
     * @return t1
     */
    public static <T> Collection<T> addAll(Collection<T> t1, Iterable<? extends T> t2) {
        for (T t : t2) {
            t1.add(t);
        }
        return t1;
    }

    /**
     * The result of diffing two sets.
     *
     * @param <T> The type of element the sets contain
     * @see CollectionUtils#diffSetsBy(java.util.Set, java.util.Set, org.gradle.api.Transformer)
     */
    public static class SetDiff<T> {
        public static class Pair<T> {
            public T left;
            public T right;
        }

        public Set<T> leftOnly = new HashSet<T>();
        public Set<Pair<T>> common = new HashSet<Pair<T>>();
        public Set<T> rightOnly = new HashSet<T>();
    }

    /**
     * Provides a “diff report” of how the two sets are similar and how they are different, comparing the entries by some aspect.
     *
     * The transformer is used to generate the value to use to compare the entries by. That is, the entries are not compared by equals by an attribute or characteristic.
     *
     * The transformer is expected to produce a unique value for each entry in a single set. Behaviour is undefined if this condition is not met.
     *
     * @param left The set on the “left” side of the comparison.
     * @param right The set on the “right” side of the comparison.
     * @param compareBy Provides the value to compare entries from either side by
     * @param <T> The type of the entry objects
     * @return A representation of the difference
     */
    public static <T> SetDiff<T> diffSetsBy(Set<? extends T> left, Set<? extends T> right, Transformer<?, T> compareBy) {
        if (left == null) {
            throw new NullPointerException("'left' set is null");
        }
        if (right == null) {
            throw new NullPointerException("'right' set is null");
        }

        SetDiff<T> setDiff = new SetDiff<T>();

        Map<Object, T> indexedLeft = collectMap(left, compareBy);
        Map<Object, T> indexedRight = collectMap(right, compareBy);

        for (Map.Entry<Object, T> leftEntry : indexedLeft.entrySet()) {
            T rightValue = indexedRight.remove(leftEntry.getKey());
            if (rightValue == null) {
                setDiff.leftOnly.add(leftEntry.getValue());
            } else {
                SetDiff.Pair<T> pair = new SetDiff.Pair<T>();
                pair.left = leftEntry.getValue();
                pair.right = rightValue;
                setDiff.common.add(pair);
            }
        }

        for (T rightValue : indexedRight.values()) {
            setDiff.rightOnly.add(rightValue);
        }

        return setDiff;
    }

    /**
     * Creates a string with {@code toString()} of each object with the given separator.
     *
     * <pre>
     * expect:
     * join(",", new Object[]{"a"}) == "a"
     * join(",", new Object[]{"a", "b", "c"}) == "a,b,c"
     * join(",", new Object[]{}) == ""
     * </pre>
     *
     * The {@code separator} must not be null and {@code objects} must not be null.
     *
     * @param separator The string by which to join each string representation
     * @param objects The objects to join the string representations of
     * @return The joined string
     */
    public static String join(String separator, Object[] objects) {
        return join(separator, objects == null ? null : Arrays.asList(objects));
    }

    /**
     * Creates a string with {@code toString()} of each object with the given separator.
     *
     * <pre>
     * expect:
     * join(",", ["a"]) == "a"
     * join(",", ["a", "b", "c"]) == "a,b,c"
     * join(",", []) == ""
     * </pre>
     *
     * The {@code separator} must not be null and {@code objects} must not be null.
     *
     * @param separator The string by which to join each string representation
     * @param objects The objects to join the string representations of
     * @return The joined string
     */
    public static String join(String separator, Iterable<?> objects) {
        if (separator == null) {
            throw new NullPointerException("The 'separator' cannot be null");
        }
        if (objects == null) {
            throw new NullPointerException("The 'objects' cannot be null");
        }

        boolean first = true;
        StringBuilder string = new StringBuilder();
        for (Object object : objects) {
            if (!first) {
                string.append(separator);
            }
            string.append(object.toString());
            first = false;
        }
        return string.toString();
    }

}