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

package org.gradle.execution.plan;

import org.gradle.internal.Cast;

import javax.annotation.CheckReturnValue;
import java.util.function.Consumer;

public interface PersistentList<T> {
    static <T> PersistentList<T> of() {
        return Cast.uncheckedCast(Nil.NIL);
    }

    static <T> PersistentList<T> of(T first) {
        return PersistentList.<T>of().add(first);
    }

    void forEach(Consumer<? super T> consumer);

    @CheckReturnValue
    PersistentList<T> add(T element);
}

class Nil<T> implements PersistentList<T> {
    public static final Nil<Object> NIL = new Nil<>();

    @Override
    public void forEach(Consumer<? super T> consumer) {
    }

    @Override
    public PersistentList<T> add(T element) {
        return new Cons<>(element, this);
    }
}

class Cons<T> implements PersistentList<T> {
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
    public PersistentList<T> add(T element) {
        return new Cons<>(element, this);
    }
}
