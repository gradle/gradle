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

package org.gradle.execution.plan;

import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

public final class NodeSets {

    public static Set<Node> newSortedNodeSet() {
        return new LazySortedReferenceHashSet<>(NodeComparator.INSTANCE);
    }

    public static List<Node> sortedListOf(Set<Node> nodes) {
        List<Node> sorted = new ArrayList<>(nodes);
        sorted.sort(NodeComparator.INSTANCE);
        return sorted;
    }

    private NodeSets() {
    }

    /**
     * A {@link ReferenceOpenHashSet reference-based hash-set} with iteration order based on a given comparator.
     *
     * @param <E> the element type
     */
    static final class LazySortedReferenceHashSet<E> extends AbstractSet<E> {

        private final Comparator<? super E> comparator;
        private final ReferenceOpenHashSet<E> set = new ReferenceOpenHashSet<>();
        private Object[] array = NO_ELEMENTS;
        private int size = 0;
        private int version = 0; // positive means unsorted, negative means sorted

        public LazySortedReferenceHashSet(Comparator<? super E> comparator) {
            this.comparator = comparator;
        }

        @Override
        public boolean add(E e) {
            if (!set.add(e)) {
                return false;
            }
            if (size >= array.length) {
                array = growArray(array);
            }
            array[size++] = e;
            version = Math.abs(version) + 1;
            return true;
        }


        @Override
        public boolean contains(Object o) {
            return set.contains(o);
        }

        @Override
        public boolean remove(Object o) {
            if (set.remove(o)) {
                int index = indexOf(o);
                if (index < 0) {
                    throw new ConcurrentModificationException();
                }
                removeIndex(index);
                return true;
            }
            return false;
        }

        @Override
        public Iterator<E> iterator() {
            if (size == 0) {
                return ObjectIterators.emptyIterator();
            }

            sort();
            return new Iterator<E>() {
                int iteratorVersion = version;
                int index = 0;
                int lastReturned = -1; // index of last element returned by next(), -1 if none or already removed

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public E next() {
                    if (version != iteratorVersion) {
                        throw new ConcurrentModificationException();
                    }
                    if (index >= size) {
                        throw new java.util.NoSuchElementException();
                    }
                    lastReturned = index;
                    return uncheckedCast(array[index++]);
                }

                @Override
                public void remove() {
                    if (version != iteratorVersion) {
                        throw new ConcurrentModificationException();
                    }
                    if (lastReturned < 0) {
                        throw new IllegalStateException();
                    }
                    E e = uncheckedCast(array[lastReturned]);
                    if (!set.remove(e)) {
                        throw new ConcurrentModificationException();
                    }
                    removeIndex(lastReturned);
                    if (lastReturned < index) {
                        index--;
                    }
                    lastReturned = -1;
                    iteratorVersion = version;
                }
            };
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int hashCode() {
            return set.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Set)) {
                return false;
            }
            return set.equals(o);
        }

        private void sort() {
            if (version <= 0) {
                return;
            }
            synchronized (this) {
                if (version <= 0) {
                    return;
                }
                Arrays.sort(uncheckedCast(array), 0, size, comparator);
                version = -version;
            }
        }

        private int indexOf(Object o) {
            for (int i = size - 1; i >= 0; i--) {
                if (array[i] == o) {
                    return i;
                }
            }
            return -1;
        }

        private void removeIndex(int index) {
            int numMoved = size - index - 1;
            if (numMoved > 0) {
                System.arraycopy(array, index + 1, array, index, numMoved);
            }
            //noinspection DataFlowIssue
            array[--size] = null; // avoid memory leak
            version = version < 0 ? version - 1 : version + 1;
        }

        private static Object[] growArray(Object[] array) {
            Object[] grow = new Object[Math.max(INITIAL_CAPACITY, array.length * 3 / 2)];
            System.arraycopy(array, 0, grow, 0, array.length);
            return grow;
        }

        static final int INITIAL_CAPACITY = 8; // package-private for tests

        private static final Object[] NO_ELEMENTS = {};
    }
}
