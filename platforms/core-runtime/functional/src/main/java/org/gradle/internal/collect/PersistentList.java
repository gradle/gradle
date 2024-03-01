/*
 * Copyright 2021 the original author or authors.
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

import javax.annotation.CheckReturnValue;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A simple persistent List implementation.
 *
 * The main use-case is to create new lists with added elements creating the minimal amount of garbage.
 * Uses Cons/Nil as building blocks.
 */
public abstract class PersistentList<T> {
    @SuppressWarnings("unchecked")
    public static <T> PersistentList<T> of() {
        return (PersistentList<T>) NIL;
    }

    public static <T> PersistentList<T> of(T first) {
        return PersistentList.<T>of().plus(first);
    }

    @SafeVarargs
    public static <T> PersistentList<T> of(T first, T second, T... rest) {
        PersistentList<T> result = of();
        for (int i = rest.length - 1; i >= 0; i--) {
            result = result.plus(rest[i]);
        }
        return result.plus(second).plus(first);
    }

    public abstract void forEach(Consumer<? super T> consumer);

    /**
     * Creates a new list with the given element as the first element in the list.
     *
     * So {@code (b : c).plus(a) == (a : b : c)}
     */
    @CheckReturnValue
    public abstract PersistentList<T> plus(T element);

    public abstract boolean isEmpty();

    private PersistentList() {}

    private static final PersistentList<Object> NIL = new PersistentList<Object>() {
        @Override
        public void forEach(Consumer<? super Object> consumer) {
        }

        @Override
        public PersistentList<Object> plus(Object element) {
            return new Cons<>(element, this);
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public String toString() {
            return "Nil";
        }
    };

    private static class Cons<T> extends PersistentList<T> {
        private final T head;
        private final PersistentList<T> tail;

        public Cons(T head, PersistentList<T> tail) {
            this.head = head;
            this.tail = tail;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            consumer.accept(head);
            tail.forEach(consumer);
        }

        @Override
        public PersistentList<T> plus(T element) {
            return new Cons<>(element, this);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Cons<?> cons = (Cons<?>) o;
            return head.equals(cons.head) && tail.equals(cons.tail);
        }

        @Override
        public int hashCode() {
            return Objects.hash(head, tail);
        }

        @Override
        public String toString() {
            return tail == NIL ? head.toString() : head + " : " + tail;
        }
    }
}
