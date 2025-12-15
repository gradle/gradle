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

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

final class PersistentSetCollector<K> implements Collector<K, PersistentSetCollector.Accumulator<K>, PersistentSet<K>> {

    static final Collector<?, ?, ?> INSTANCE = new PersistentSetCollector<>();

    public static final class Accumulator<K> {

        // TODO: introduce transient
        private PersistentSet<K> set = PersistentSet.of();

        public void accept(K k) {
            set = set.plus(k);
        }

        public Accumulator<K> combine(Accumulator<K> c2) {
            set = set.union(c2.set);
            return this;
        }

        public PersistentSet<K> finish() {
            return set;
        }
    }

    @Override
    public Supplier<Accumulator<K>> supplier() {
        return Accumulator::new;
    }

    @Override
    public BiConsumer<Accumulator<K>, K> accumulator() {
        return Accumulator::accept;
    }

    @Override
    public BinaryOperator<Accumulator<K>> combiner() {
        return Accumulator::combine;
    }

    @Override
    public Function<Accumulator<K>, PersistentSet<K>> finisher() {
        return Accumulator::finish;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
    }
}
