/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.collect;

import org.gradle.util.internal.ArrayUtils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import static org.gradle.internal.collect.ArrayCopy.EMPTY_ARRAY;

/// A [PersistentArray] with more than [32][#WIDTH] elements implemented as a _persistent bitmapped vector trie_,
/// providing effective *O(1)* mutation and random access.
///
/// See Chapter 2 of [Improving RRB-Tree Performance through Transience](https://hypirion.com/thesis.pdf) for a full account.
///
/// This implementation includes the *tail optimization* described in section 2.7 of the aforementioned thesis,
/// but not the *display* from section 2.8.
///
final class PersistentArrayTrie<T> implements PersistentArray<T> {

    static final int WIDTH = 32;
    private static final int BITS = 5;
    private static final int BITMASK = 0b11111;

    private final int size;
    private final int shift;
    private final Object[] root;
    private final Object[] tail; // never empty

    public PersistentArrayTrie(int size, int shift, Object[] root, Object[] tail) {
        this.size = size;
        this.shift = shift;
        this.root = root;
        this.tail = tail;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        for (int i = 0; i < size; i += WIDTH) {
            hashCode += 31 * hashCode + Arrays.hashCode(arrayStartingAt(i));
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        // ✅ Only compares with PersistentArrayTrie. Safe because PersistentArray implementations
        // have non-overlapping size ranges (1, 2-32, 33+), so same-content arrays always have the same type.
        if (obj instanceof PersistentArrayTrie) {
            PersistentArrayTrie<?> other = (PersistentArrayTrie<?>) obj;
            if (size != other.size) {
                return false;
            }
            for (int i = 0; i < size; i += WIDTH) {
                if (!Arrays.equals(arrayStartingAt(i), other.arrayStartingAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return ToString.nonEmptyIterator(iterator());
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get(int index) {
        int tailOffset = size - tail.length;
        if (index < tailOffset) {
            if (index < 0) {
                throw indexOutOfBounds(index);
            }
            Object[] array = root;
            for (int i = shift; i > 0; i -= BITS) {
                int nodeIndex = (index >>> i) & BITMASK;
                array = (Object[]) array[nodeIndex];
            }
            return (T) array[index & BITMASK];
        } else {
            if (index >= size) {
                throw indexOutOfBounds(index);
            }
            return (T) tail[index - tailOffset];
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getLast() {
        return (T) tail[tail.length - 1];
    }

    @Override
    public PersistentArray<T> plus(T value) {
        if (tail.length < WIDTH) {
            return new PersistentArrayTrie<>(
                size + 1,
                shift,
                root,
                ArrayCopy.append(tail, value)
            );
        }

        int newShift;
        Object[] newRoot;
        if (size - tail.length != WIDTH << shift) {
            MutableBoolean overflow = new MutableBoolean(false);
            newRoot = pushTail(shift, root, tail, overflow);
            assert !overflow.value;
            newShift = shift;
        } else {
            // array is fully dense and must grow
            newRoot = new Object[]{root, createTailPath(shift, tail)};
            newShift = shift + BITS;
        }
        return new PersistentArrayTrie<>(
            size + 1,
            newShift,
            newRoot,
            new Object[]{value}
        );
    }

    private static Object createTailPath(int shift, Object[] tail) {
        Object[] path = tail;
        for (int i = shift; i > 0; i -= BITS) {
            path = new Object[]{path};
        }
        return path;
    }

    private static Object[] pushTail(int shift, Object[] nodes, Object[] tail, MutableBoolean overflow) {
        if (shift > BITS) {
            int length = nodes.length;
            int tailIndex = length - 1;
            Object[] newTail = pushTail(shift - BITS, (Object[]) nodes[tailIndex], tail, overflow);
            if (!overflow.value) {
                return ArrayCopy.replaceAt(tailIndex, nodes, newTail);
            }
            if (length < WIDTH) {
                overflow.value = false;
                return ArrayCopy.append(nodes, newTail);
            }
            return new Object[]{newTail};
        } else {
            if (nodes.length < WIDTH) {
                return ArrayCopy.append(nodes, tail);
            }
            overflow.value = true;
            return new Object[]{tail};
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            int index = 0;
            final int count = size;
            Object[] array = EMPTY_ARRAY;

            @Override
            public boolean hasNext() {
                return index < count;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T next() {
                if (index < count) {
                    final int next = index & BITMASK;
                    if (next == 0) {
                        array = arrayStartingAt(index);
                    }
                    index++;
                    return (T) array[next];
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    @Override
    public boolean contains(T value) {
        for (int i = 0; i < size; i += WIDTH) {
            if (ArrayUtils.contains(arrayStartingAt(i), value)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void forEach(Consumer<? super T> action) {
        for (int i = 0; i < size; i += WIDTH) {
            for (Object value : arrayStartingAt(i)) {
                action.accept((T) value);
            }
        }
    }

    private Object[] arrayStartingAt(int index) {
        if (index < size - tail.length) {
            Object[] array = root;
            for (int i = shift; i > 0; i -= BITS) {
                int nodeIndex = (index >>> i) & BITMASK;
                array = (Object[]) array[nodeIndex];
            }
            return array;
        } else {
            return tail;
        }
    }

    static IndexOutOfBoundsException indexOutOfBounds(int index) {
        return new IndexOutOfBoundsException("Index out of range: " + index);
    }
}
