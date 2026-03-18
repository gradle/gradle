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

package org.gradle.internal.collect.bench;

import org.gradle.internal.collect.PersistentSet;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/// Is it better to have a single default implementation in [PersistentSet] or multiple specialized implementations?
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
public class PersistentSetPolymorphismBenchmark {

    PersistentSet<Integer> set0 = PersistentSet.of();
    PersistentSet<Integer> set1 = PersistentSet.of(1);
    PersistentSet<Integer> setTrie = PersistentSet.of(1, 2, 3);
    List<PersistentSet<Integer>> sets = Arrays.asList(set0, set1, setTrie);
    Random random = new Random();

    @Benchmark
    public void groupBy0(Blackhole blackhole) {
        groupyBy(blackhole, set0);
    }

    @Benchmark
    public void groupBy1(Blackhole blackhole) {
        groupyBy(blackhole, set1);
    }

    @Benchmark
    public void groupByTrie(Blackhole blackhole) {
        groupyBy(blackhole, setTrie);
    }

    @Benchmark
    public void groupByRandom(Blackhole blackhole) {
        groupyBy(blackhole, sets.get(random.nextInt(sets.size())));
    }

    private void groupyBy(Blackhole blackhole, PersistentSet<Integer> set0) {
        blackhole.consume(set0.groupBy(it -> it % 2 == 0));
    }
}
