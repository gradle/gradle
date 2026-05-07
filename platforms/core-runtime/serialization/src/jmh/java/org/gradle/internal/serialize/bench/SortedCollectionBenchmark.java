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

package org.gradle.internal.serialize.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Benchmarks comparing two approaches for sorting map keys / set elements
 * before serialization:
 *
 * <ul>
 *   <li><b>array</b> (current): {@code keySet().toArray(new Comparable[0])} + {@code Arrays.sort()}</li>
 *   <li><b>list</b> (reviewer suggestion): {@code new ArrayList<>(keySet())} + {@code list.sort(null)}</li>
 * </ul>
 *
 * @see <a href="https://github.com/gradle/gradle/pull/37290/files#r2996401630">PR review comment</a>
 */
@Fork(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@State(Scope.Benchmark)
public class SortedCollectionBenchmark {

    /**
     * Size parameters chosen to cover realistic instrumentation analysis scenarios:
     * small (8), medium (64), large (512).
     */
    @Param({"8", "64"})
    int size;

    private Map<String, Set<String>> map;
    private Set<String> set;

    @Setup(Level.Iteration)
    public void setup() {
        Random rng = new Random(42);
        map = new HashMap<>(size);
        set = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            // Simulate class-name-like keys (the real-world usage)
            String key = "org/gradle/internal/Class" + rng.nextInt(100_000);
            set.add(key);

            Set<String> values = new HashSet<>();
            int valueCount = 1 + rng.nextInt(5);
            for (int j = 0; j < valueCount; j++) {
                values.add("org/gradle/api/Type" + rng.nextInt(100_000));
            }
            map.put(key, values);
        }
    }

    // ---- Map key sorting ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Benchmark
    public void mapKeys_arraySorted(Blackhole bh) {
        // Mirrors the production code in SortedMapSerializer.write():
        // K[] sortedKeys = (K[]) value.keySet().toArray(new Comparable[0]);
        // The unchecked cast to K[] is erased at runtime, so the actual
        // array type is Comparable[].
        Comparable[] sortedKeys = map.keySet().toArray(new Comparable[0]);
        Arrays.sort(sortedKeys);
        for (Comparable key : sortedKeys) {
            bh.consume(key);
            bh.consume(map.get(key));
        }
    }

    @Benchmark
    public void mapKeys_listSorted(Blackhole bh) {
        List<String> sortedKeys = new ArrayList<>(map.keySet());
        sortedKeys.sort(null);
        for (String key : sortedKeys) {
            bh.consume(key);
            bh.consume(map.get(key));
        }
    }

    // ---- Set element sorting ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Benchmark
    public void setElements_arraySorted(Blackhole bh) {
        Comparable[] sorted = set.toArray(new Comparable[0]);
        Arrays.sort(sorted);
        for (Comparable elem : sorted) {
            bh.consume(elem);
        }
    }

    @Benchmark
    public void setElements_listSorted(Blackhole bh) {
        List<String> sorted = new ArrayList<>(set);
        sorted.sort(null);
        for (String elem : sorted) {
            bh.consume(elem);
        }
    }
}
