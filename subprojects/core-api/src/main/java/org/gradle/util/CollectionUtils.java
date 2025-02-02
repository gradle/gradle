/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.Pair;
import org.gradle.internal.Transformers;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.gradle.internal.Cast.cast;
import static org.gradle.internal.Cast.castNullable;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 * <p>
 * Plugins should prefer external collection frameworks over this class.
 * Internally, all code should use {@link org.gradle.util.internal.CollectionUtils}.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public abstract class CollectionUtils {

    private static void logDeprecation(int upgradeGuideMajorVersion) {
        DeprecationLogger.deprecateType(CollectionUtils.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(upgradeGuideMajorVersion, "org_gradle_util_reports_deprecations")
            .nagUser();
    }

    public CollectionUtils() {
        logDeprecation(7);
    }

    /**
     * Returns null if the collection is empty otherwise expects a {@link #single(Iterable)} element to be found.
     */
    @Nullable
    public static <T> T findSingle(Iterable<T> source) {
        logDeprecation(7);
        return Iterables.isEmpty(source) ? null : singleInternal(source);
    }

    /**
     * Returns the single element in the collection or throws.
     */
    public static <T> T single(Iterable<? extends T> source) {
        logDeprecation(7);
        return singleInternal(source);
    }

    private static <T> T singleInternal(Iterable<? extends T> source) {
        Iterator<? extends T> iterator = source.iterator();
        if (!iterator.hasNext()) {
            throw new NoSuchElementException("Expecting collection with single element, got none.");
        }
        T element = iterator.next();
        if (iterator.hasNext()) {
            throw new IllegalArgumentException("Expecting collection with single element, got multiple.");
        }
        return element;
    }

    public static <T> Collection<? extends T> checkedCast(Class<T> type, Collection<?> input) {
        logDeprecation(7);
        for (Object o : input) {
            castNullable(type, o);
        }
        return uncheckedNonnullCast(input);
    }

    @Nullable
    public static <T> T findFirst(Iterable<? extends T> source, Spec<? super T> filter) {
        logDeprecation(7);
        for (T item : source) {
            if (filter.isSatisfiedBy(item)) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    public static <T> T findFirst(T[] source, Spec<? super T> filter) {
        logDeprecation(7);
        for (T thing : source) {
            if (filter.isSatisfiedBy(thing)) {
                return thing;
            }
        }

        return null;
    }

    public static <T> T first(Iterable<? extends T> source) {
        logDeprecation(7);
        return source.iterator().next();
    }

    public static <T> boolean any(Iterable<? extends T> source, Spec<? super T> filter) {
        return findFirst(source, filter) != null;
    }

    public static <T> boolean any(T[] source, Spec<? super T> filter) {
        return findFirst(source, filter) != null;
    }

    public static <T> Set<T> filter(Set<? extends T> set, Spec<? super T> filter) {
        return filter(set, new LinkedHashSet<T>(), filter);
    }

    public static <T> List<T> filter(List<? extends T> list, Spec<? super T> filter) {
        return filter(list, new ArrayList<>(list.size()), filter);
    }

    public static <T> List<T> filter(T[] array, Spec<? super T> filter) {
        return filter(Arrays.asList(array), new ArrayList<>(array.length), filter);
    }


    /**
     * Returns a sorted copy of the provided collection of things. Uses the provided comparator to sort.
     */
    public static <T> List<T> sort(Iterable<? extends T> things, Comparator<? super T> comparator) {
        List<T> copy = toMutableList(things);
        Collections.sort(copy, comparator);
        return copy;
    }

    /**
     * Returns a sorted copy of the provided collection of things. Uses the natural ordering of the things.
     */
    public static <T extends Comparable<T>> List<T> sort(Iterable<T> things) {
        List<T> copy = toMutableList(things);
        Collections.sort(copy);
        return copy;
    }

    public static <T, C extends Collection<T>> C filter(Iterable<? extends T> source, C destination, Spec<? super T> filter) {
        logDeprecation(7);
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
        logDeprecation(7);
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (filter.isSatisfiedBy(entry)) {
                destination.put(entry.getKey(), entry.getValue());
            }
        }

        return destination;
    }

    public static <R, I> R[] collectArray(I[] list, Class<R> newType, Transformer<? extends R, ? super I> transformer) {
        @SuppressWarnings("unchecked") R[] destination = (R[]) Array.newInstance(newType, list.length);
        return collectArray(list, destination, transformer);
    }

    public static <R, I> R[] collectArray(I[] list, R[] destination, Transformer<? extends R, ? super I> transformer) {
        logDeprecation(7);
        assert list.length <= destination.length;
        for (int i = 0; i < list.length; ++i) {
            destination[i] = transformer.transform(list[i]);
        }
        return destination;
    }

    public static <R, I> List<R> collect(I[] list, Transformer<? extends R, ? super I> transformer) {
        return collect(Arrays.asList(list), transformer);
    }

    public static <R, I> Set<R> collect(Set<? extends I> set, Transformer<? extends R, ? super I> transformer) {
        return collect(set, new HashSet<R>(set.size()), transformer);
    }

    public static <R, I> List<R> collect(Iterable<? extends I> source, Transformer<? extends R, ? super I> transformer) {
        if (source instanceof Collection<?>) {
            Collection<? extends I> collection = uncheckedNonnullCast(source);
            return collect(source, new ArrayList<R>(collection.size()), transformer);
        } else {
            return collect(source, new LinkedList<R>(), transformer);
        }
    }

    public static <R, I, C extends Collection<R>> C collect(Iterable<? extends I> source, C destination, Transformer<? extends R, ? super I> transformer) {
        logDeprecation(7);
        return collectInternal(source, destination, transformer);
    }

    private static <R, I, C extends Collection<R>> C collectInternal(Iterable<? extends I> source, C destination, Transformer<? extends R, ? super I> transformer) {
        for (I item : source) {
            destination.add(transformer.transform(item));
        }
        return destination;
    }

    public static List<String> toStringList(Iterable<?> iterable) {
        return collect(iterable, new LinkedList<String>(), String::valueOf);
    }

    /**
     * Recursively unpacks all the given things into a flat list.
     *
     * Nulls are not removed, they are left intact.
     *
     * @param things The things to flatten
     * @return A flattened list of the given things
     */
    public static List<?> flattenCollections(Object... things) {
        return flattenCollections(Object.class, things);
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
    public static <T> List<T> flattenCollections(Class<T> type, Object... things) {
        logDeprecation(7);
        return flattenCollectionsInternal(type, things);
    }

    private static <T> List<T> flattenCollectionsInternal(Class<T> type, Object... things) {
        if (things == null) {
            return Collections.singletonList(null);
        } else if (things.length == 0) {
            return Collections.emptyList();
        } else if (things.length == 1) {
            Object thing = things[0];

            if (thing == null) {
                return Collections.singletonList(null);
            }

            // Casts to Class below are to workaround Eclipse compiler bug
            // See: https://github.com/gradle/gradle/pull/200

            if (thing.getClass().isArray()) {
                Object[] thingArray = (Object[]) thing;
                List<T> list = new ArrayList<T>(thingArray.length);
                for (Object thingThing : thingArray) {
                    list.addAll(flattenCollections(type, thingThing));
                }
                return list;
            }

            if (thing instanceof Collection) {
                Collection<?> collection = (Collection<?>) thing;
                List<T> list = new ArrayList<T>();
                for (Object element : collection) {
                    list.addAll(flattenCollections(type, element));
                }
                return list;
            }

            return Collections.singletonList(cast(type, thing));
        } else {
            List<T> list = new ArrayList<T>();
            for (Object thing : things) {
                list.addAll(flattenCollections(type, thing));
            }
            return list;
        }
    }

    public static <T> List<T> toList(Iterable<? extends T> things) {
        if (things instanceof List) {
            logDeprecation(7);
            @SuppressWarnings("unchecked") List<T> castThings = (List<T>) things;
            return castThings;
        }
        return toMutableList(things);
    }

    public static <T> List<T> toList(Enumeration<? extends T> things) {
        logDeprecation(7);
        AbstractList<T> list = new ArrayList<T>();
        while (things.hasMoreElements()) {
            list.add(things.nextElement());
        }
        return list;
    }

    private static <T> List<T> toMutableList(Iterable<? extends T> things) {
        logDeprecation(7);
        if (things == null) {
            return new ArrayList<T>(0);
        }
        List<T> list = new ArrayList<T>();
        for (T thing : things) {
            list.add(thing);
        }
        return list;
    }


    public static <T> List<T> intersection(Collection<? extends Collection<T>> availableValuesByDescriptor) {
        logDeprecation(7);
        List<T> result = new ArrayList<T>();
        Iterator<? extends Collection<T>> iterator = availableValuesByDescriptor.iterator();
        if (iterator.hasNext()) {
            Collection<T> firstSet = iterator.next();
            result.addAll(firstSet);
            while (iterator.hasNext()) {
                Collection<T> next = iterator.next();
                result.retainAll(next);
            }
        }
        return result;

    }

    public static <T> List<T> toList(T[] things) {
        logDeprecation(7);
        if (things == null || things.length == 0) {
            return new ArrayList<T>(0);
        }

        List<T> list = new ArrayList<T>(things.length);
        Collections.addAll(list, things);
        return list;
    }

    public static <T> Set<T> toSet(Iterable<? extends T> things) {
        logDeprecation(7);
        if (things == null) {
            return new HashSet<T>(0);
        }
        if (things instanceof Set) {
            @SuppressWarnings("unchecked") Set<T> castThings = (Set<T>) things;
            return castThings;
        }

        Set<T> set = new LinkedHashSet<T>();
        for (T thing : things) {
            set.add(thing);
        }
        return set;
    }

    public static <E> List<E> compact(List<E> list) {
        logDeprecation(7);
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
        logDeprecation(7);
        return stringizeInternal(source, destination);
    }

    public static List<String> stringize(Collection<?> source) {
        logDeprecation(8);
        return stringizeInternal(source, new ArrayList<String>(source.size()));
    }

    private static <C extends Collection<String>> C stringizeInternal(Iterable<?> source, C destination) {
        return collectInternal(source, destination, String::valueOf);
    }

    public static <E> boolean replace(List<E> list, Spec<? super E> filter, Transformer<? extends E, ? super E> transformer) {
        logDeprecation(7);
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
        logDeprecation(7);
        for (V item : items) {
            destination.put(keyGenerator.transform(item), item);
        }
    }

    /**
     * Given a set of values, derive a set of keys and return a map
     */
    public static <K, V> Map<K, V> collectMap(Iterable<? extends V> items, Transformer<? extends K, ? super V> keyGenerator) {
        Map<K, V> map = new LinkedHashMap<K, V>();
        collectMap(map, items, keyGenerator);
        return map;
    }

    public static <K, V> void collectMapValues(Map<K, V> destination, Iterable<? extends K> keys, Transformer<? extends V, ? super K> keyGenerator) {
        logDeprecation(7);
        for (K item : keys) {
            destination.put(item, keyGenerator.transform(item));
        }
    }

    /**
     * Given a set of keys, derive a set of values and return a map
     */
    public static <K, V> Map<K, V> collectMapValues(Iterable<? extends K> keys, Transformer<? extends V, ? super K> keyGenerator) {
        Map<K, V> map = new LinkedHashMap<K, V>();
        collectMapValues(map, keys, keyGenerator);
        return map;
    }

    public static <T> boolean every(Iterable<? extends T> things, Spec<? super T> predicate) {
        logDeprecation(7);
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
    public static <T, C extends Collection<? super T>> C addAll(C t1, Iterable<? extends T> t2) {
        logDeprecation(7);
        for (T t : t2) {
            t1.add(t);
        }
        return t1;
    }

    /**
     * Utility for adding an array to a collection.
     *
     * @param t1 The collection to add to
     * @param t2 The iterable to add each item of to the collection
     * @param <T> The element type of t1
     * @return t1
     */
    // TODO Use @SafeVarargs and make method final
    @SuppressWarnings("unchecked")
    public static <T, C extends Collection<? super T>> C addAll(C t1, T... t2) {
        logDeprecation(7);
        Collections.addAll(t1, t2);
        return t1;
    }

    /**
     * The result of diffing two sets.
     *
     * @param <T> The type of element the sets contain
     * @see CollectionUtils#diffSetsBy(java.util.Set, java.util.Set, org.gradle.api.Transformer)
     */
    public static class SetDiff<T> {
        public Set<T> leftOnly = new HashSet<T>();
        public Set<Pair<T, T>> common = new HashSet<Pair<T, T>>();
        public Set<T> rightOnly = new HashSet<T>();

        public SetDiff() {
            this(true);
        }

        private SetDiff(boolean logDeprecation) {
            if (logDeprecation) {
                logDeprecation(7);
            }
        }
    }

    /**
     * Provides a "diff report" of how the two sets are similar and how they are different, comparing the entries by some aspect.
     *
     * The transformer is used to generate the value to use to compare the entries by. That is, the entries are not compared by equals by an attribute or characteristic.
     *
     * The transformer is expected to produce a unique value for each entry in a single set. Behaviour is undefined if this condition is not met.
     *
     * @param left The set on the "left" side of the comparison.
     * @param right The set on the "right" side of the comparison.
     * @param compareBy Provides the value to compare entries from either side by
     * @param <T> The type of the entry objects
     * @return A representation of the difference
     */
    public static <T> SetDiff<T> diffSetsBy(Set<? extends T> left, Set<? extends T> right, Transformer<?, T> compareBy) {
        logDeprecation(7);
        if (left == null) {
            throw new NullPointerException("'left' set is null");
        }
        if (right == null) {
            throw new NullPointerException("'right' set is null");
        }

        SetDiff<T> setDiff = new SetDiff<T>(false);

        Map<Object, T> indexedLeft = collectMap(left, compareBy);
        Map<Object, T> indexedRight = collectMap(right, compareBy);

        for (Map.Entry<Object, T> leftEntry : indexedLeft.entrySet()) {
            T rightValue = indexedRight.remove(leftEntry.getKey());
            if (rightValue == null) {
                setDiff.leftOnly.add(leftEntry.getValue());
            } else {
                Pair<T, T> pair = Pair.of(leftEntry.getValue(), rightValue);
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
        logDeprecation(7);
        if (separator == null) {
            throw new NullPointerException("The 'separator' cannot be null");
        }
        if (objects == null) {
            throw new NullPointerException("The 'objects' cannot be null");
        }

        StringBuilder string = new StringBuilder();
        Iterator<?> iterator = objects.iterator();
        if (iterator.hasNext()) {
            string.append(iterator.next().toString());
            while (iterator.hasNext()) {
                string.append(separator);
                string.append(iterator.next().toString());
            }
        }
        return string.toString();
    }

    /**
     * Partition given Collection into a Pair of Collections.
     *
     * <pre>Left</pre> Collection containing entries that satisfy the given predicate
     * <pre>Right</pre> Collection containing entries that do NOT satisfy the given predicate
     */
    public static <T> Pair<Collection<T>, Collection<T>> partition(Iterable<T> items, Spec<? super T> predicate) {
        logDeprecation(7);
        Preconditions.checkNotNull(items, "Cannot partition null Collection");
        Preconditions.checkNotNull(predicate, "Cannot apply null Spec when partitioning");

        Collection<T> left = new LinkedList<T>();
        Collection<T> right = new LinkedList<T>();

        for (T item : items) {
            if (predicate.isSatisfiedBy(item)) {
                left.add(item);
            } else {
                right.add(item);
            }
        }

        return Pair.of(left, right);
    }

    /**
     * Injection step.
     *
     * @param <T> target type.
     * @param <I> item type.
     */
    @Deprecated
    public static class InjectionStep<T, I> {
        private final T target;
        private final I item;

        public InjectionStep(T target, I item) {
            this(target, item, true);
        }

        private InjectionStep(T target, I item, boolean logDeprecation) {
            this.target = target;
            this.item = item;
            if (logDeprecation) {
                logDeprecation(7);
            }
        }

        public T getTarget() {
            return target;
        }

        public I getItem() {
            return item;
        }
    }

    public static <T, I> T inject(T target, Iterable<? extends I> items, Action<InjectionStep<T, I>> action) {
        logDeprecation(7);
        if (target == null) {
            throw new NullPointerException("The 'target' cannot be null");
        }
        if (items == null) {
            throw new NullPointerException("The 'items' cannot be null");
        }
        if (action == null) {
            throw new NullPointerException("The 'action' cannot be null");
        }

        for (I item : items) {
            action.execute(new InjectionStep<T, I>(target, item, false));
        }
        return target;
    }

    public static <K, V> Map<K, Collection<V>> groupBy(Iterable<? extends V> iterable, Transformer<? extends K, V> grouper) {
        logDeprecation(7);
        ImmutableListMultimap.Builder<K, V> builder = ImmutableListMultimap.builder();

        for (V element : iterable) {
            K key = grouper.transform(element);
            builder.put(key, element);
        }

        return builder.build().asMap();
    }

    public static <T> Iterable<? extends T> unpack(final Iterable<? extends Factory<? extends T>> factories) {
        logDeprecation(7);
        return new Iterable<T>() {
            private final Iterator<? extends Factory<? extends T>> delegate = factories.iterator();

            @Override
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    @Override
                    public boolean hasNext() {
                        return delegate.hasNext();
                    }

                    @Override
                    public T next() {
                        return delegate.next().create();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    @Nullable
    public static <T> List<T> nonEmptyOrNull(Iterable<T> iterable) {
        logDeprecation(7);
        ImmutableList<T> list = ImmutableList.copyOf(iterable);
        return list.isEmpty() ? null : list;
    }

    public static String asCommandLine(Iterable<String> arguments) {
        logDeprecation(7);
        return Joiner.on(" ").join(collect(arguments, Transformers.asSafeCommandLineArgument()));
    }
}
