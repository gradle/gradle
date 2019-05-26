/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.reflect;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.HashFunction;
import org.gradle.internal.hash.Hashing;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark                               Mode  Cnt   Score   Error  Units
 * HashCodeBenchmark.hashCode_and_equals  thrpt   20  83.162 ± 6.121  ops/s
 **/
@Fork(2)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = SECONDS)
public class HashCodeBenchmark {
    final List<HashCode> values = new ArrayList<HashCode>();

    @Setup
    public void prepare() {
        HashFunction hashFunction = Hashing.md5();
        for (int i = 0; i < 100000; i++) {
            HashCode hashCode = hashFunction.hashBytes(Ints.toByteArray(i));
            values.add(hashCode);
        }

        int byteCount = hashFunction.byteCount();
        System.out.println("Byte count: " + byteCount);
        for (HashCode value : values) {
            Preconditions.checkArgument(value.toByteArray().length == byteCount);
        }
    }

    @Benchmark
    public Object hashCode_and_equals() {
        Set<HashCode> set = Sets.newHashSetWithExpectedSize(1); // we want many collisions
        set.addAll(values);
        set.addAll(values); // we need duplicates
        Preconditions.checkArgument(set.size() == values.size());
        return set;
    }
}
