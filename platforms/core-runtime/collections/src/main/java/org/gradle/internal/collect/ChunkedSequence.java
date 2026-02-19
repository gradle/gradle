/*
 * Copyright 2026 the original author or authors.
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

import java.util.Collections;
import java.util.Iterator;

@SuppressWarnings("DoNotCallSuggester")
public interface ChunkedSequence<T> extends Iterable<T> {

    boolean isEmpty();

    ChunkedSequence<T> prepend(T chunk);

    ChunkedSequence<T> concat(ChunkedSequence<T> seq);

    static <T> ChunkedSequence<T> of() {
        return new ChunkedSequenceZero<>();
    }

    static <T> ChunkedSequence<T> of(T chunk) {
        return new ChunkedSequence1<>(chunk);
    }

    class ChunkedSequenceZero<T> implements ChunkedSequence<T> {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ChunkedSequence<T> prepend(T chunk) {
            return new ChunkedSequence1<>(chunk);
        }

        @Override
        public ChunkedSequence<T> concat(ChunkedSequence<T> seq) {
            return seq;
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.emptyIterator();
        }
    }

    class ChunkedSequence1<T> implements ChunkedSequence<T> {
        private final T chunk;

        public ChunkedSequence1(T chunk) {
            this.chunk = chunk;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ChunkedSequence<T> prepend(T chunk) {
            return new ChunkedSequenceMany<>(ChunkedSequence.of(chunk), this);
        }

        @Override
        public ChunkedSequence<T> concat(ChunkedSequence<T> seq) {
            return seq.isEmpty() ? this : new ChunkedSequenceMany<>(this, seq);
        }

        @Override
        public Iterator<T> iterator() {
            return new SingletonIterator<>(chunk);
        }
    }

    class ChunkedSequenceMany<T> implements ChunkedSequence<T> {
        private final ChunkedSequence<T> left;
        private final ChunkedSequence<T> right;

        public ChunkedSequenceMany(ChunkedSequence<T> left, ChunkedSequence<T> right) {
            assert !(left instanceof ChunkedSequenceZero);
            assert !(right instanceof ChunkedSequenceZero);
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ChunkedSequence<T> prepend(T chunk) {
            return new ChunkedSequenceMany<>(ChunkedSequence.of(chunk), this);
        }

        @Override
        public ChunkedSequence<T> concat(ChunkedSequence<T> seq) {
            return seq.isEmpty() ? this : new ChunkedSequenceMany<>(this, seq);
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                private final Iterator<T> leftIterator = left.iterator();
                private final Iterator<T> rightIterator = right.iterator();

                @Override
                public boolean hasNext() {
                    return leftIterator.hasNext() || rightIterator.hasNext();
                }

                @Override
                public T next() {
                    if (leftIterator.hasNext()) {
                        return leftIterator.next();
                    }
                    return rightIterator.next();
                }
            };
        }
    }
}
