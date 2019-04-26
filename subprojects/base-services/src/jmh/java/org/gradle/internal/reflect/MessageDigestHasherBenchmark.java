/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.HashFunction;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark                               Mode  Cnt          Score          Error  Units
 * MessageDigestHasherBenchmark.md5_int   thrpt    5   17072888.496 ±   302858.912  ops/s
 * MessageDigestHasherBenchmark.md5_long  thrpt    5   17727372.147 ±   151062.619  ops/s
 * MessageDigestHasherBenchmark.md5_null  thrpt    5   18030982.386 ±   103535.132  ops/s
 * MessageDigestHasherBenchmark.sha1_int  thrpt    5   13559438.368 ±   149806.651  ops/s
 * MessageDigestHasherBenchmark.sha1_long thrpt    5   11535723.755 ±   155336.223  ops/s
 * MessageDigestHasherBenchmark.sha1_null thrpt    5   13357694.157 ±    50179.359  ops/s
 **/
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class MessageDigestHasherBenchmark {
    HashFunction sha1, md5;

    @Setup
    public void setup() {
        sha1 = Hashing.sha1();
        md5 = Hashing.md5();
    }

    @Benchmark
    public HashCode sha1_int() {
        Hasher hasher = sha1.defaultHasher();
        hasher.putInt(Integer.MAX_VALUE);
        return hasher.hash();
    }

    @Benchmark
    public HashCode sha1_long() {
        Hasher hasher = sha1.defaultHasher();
        hasher.putLong(Long.MAX_VALUE);
        return hasher.hash();
    }

    @Benchmark
    public HashCode sha1_null() {
        Hasher hasher = sha1.defaultHasher();
        hasher.putNull();
        return hasher.hash();
    }

    @Benchmark
    public HashCode md5_int() {
        Hasher hasher = md5.defaultHasher();
        hasher.putInt(Integer.MAX_VALUE);
        return hasher.hash();
    }

    @Benchmark
    public HashCode md5_long() {
        Hasher hasher = md5.defaultHasher();
        hasher.putLong(Long.MAX_VALUE);
        return hasher.hash();
    }

    @Benchmark
    public HashCode md5_null() {
        Hasher hasher = md5.defaultHasher();
        hasher.putNull();
        return hasher.hash();
    }
}
