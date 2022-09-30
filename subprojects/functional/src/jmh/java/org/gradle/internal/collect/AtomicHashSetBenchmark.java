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

package org.gradle.internal.collect;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

@Fork(1)
@Threads(4)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
public class AtomicHashSetBenchmark {

    @Param({"1", "4", "16", "512", "1024", "65536"})
    int setSize;

    CopyOnWriteArraySet<Integer> cowSet;
    AtomicHashSet<Integer> atomicHashSet;

    @Setup(Level.Iteration)
    public void setup() throws CloneNotSupportedException {
        cowSet = cowOf(setSize);
        atomicHashSet = atomicSetOf(setSize);
    }

    @Benchmark
    public void atomicSetContains(Blackhole blackhole) {
        forEach(1, 1000, i -> blackhole.consume(atomicHashSet.contains(i)));
    }

    @Benchmark
    public void cowSetContains(Blackhole blackhole) {
        forEach(1, 1000, i -> blackhole.consume(cowSet.contains(i)));
    }

    @Benchmark
    public void atomicSetAdd(Blackhole blackhole) {
        blackhole.consume(atomicSetOf(setSize));
    }

    @Benchmark
    public void cowSetAdd(Blackhole blackhole) {
        blackhole.consume(cowOf(setSize));
    }

    private static AtomicHashSet<Integer> atomicSetOf(int count) {
        AtomicHashSet<Integer> set = new AtomicHashSet<>();
        forEach(1, count, set::add);
        return set;
    }

    private static CopyOnWriteArraySet<Integer> cowOf(int count) {
        CopyOnWriteArraySet<Integer> set = new CopyOnWriteArraySet<>();
        forEach(1, count, set::add);
        return set;
    }

    private static void forEach(int from, int to, Consumer<Integer> action) {
        assert from <= to;
        for (int i = from; i <= to; ++i) {
            action.accept(i);
        }
    }
}
