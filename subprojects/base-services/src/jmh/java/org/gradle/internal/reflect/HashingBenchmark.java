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

import org.apache.commons.lang.RandomStringUtils;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.HashFunction;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark                                             (hashSize)   Mode  Cnt           Score           Error  Units
 * HashingBenchmark.md5                                          10  thrpt    5     3220072.840 ±    153467.463  ops/s
 * HashingBenchmark.md5:·sun.gc.collector.0.invocations          10  thrpt    5           7.800 ±         1.722
 * HashingBenchmark.md5                                        1024  thrpt    5     1126665.490 ±     53890.024  ops/s
 * HashingBenchmark.md5:·sun.gc.collector.0.invocations        1024  thrpt    5           2.600 ±         2.109
 * HashingBenchmark.md5                                       65536  thrpt    5       28483.327 ±      1419.431  ops/s
 * HashingBenchmark.md5:·sun.gc.collector.0.invocations       65536  thrpt    5             ≈ 0
 * HashingBenchmark.md5                                    67108864  thrpt    5          30.371 ±         0.497  ops/s
 * HashingBenchmark.md5:·sun.gc.collector.0.invocations    67108864  thrpt    5             ≈ 0
 * HashingBenchmark.sha1                                         10  thrpt    5     2695525.955 ±    131374.572  ops/s
 * HashingBenchmark.sha1:·sun.gc.collector.0.invocations         10  thrpt    5           6.600 ±         2.109
 * HashingBenchmark.sha1                                       1024  thrpt    5      847143.033 ±     39044.031  ops/s
 * HashingBenchmark.sha1:·sun.gc.collector.0.invocations       1024  thrpt    5           2.200 ±         1.722
 * HashingBenchmark.sha1                                      65536  thrpt    5       19323.252 ±       694.755  ops/s
 * HashingBenchmark.sha1:·sun.gc.collector.0.invocations      65536  thrpt    5             ≈ 0
 * HashingBenchmark.sha1                                   67108864  thrpt    5          20.130 ±         2.504  ops/s
 * HashingBenchmark.sha1:·sun.gc.collector.0.invocations   67108864  thrpt    5             ≈ 0
 **/
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class HashingBenchmark {
    private static final HashCode DUMMY_HASH = HashCode.fromInt(1);
    private static final String DUMMY_STRING = "dummy";
    private static final byte[] DUMMY_BYTES = DUMMY_STRING.getBytes();

    @Param({"10", "1024", "65536", "67108864"})
    int hashSize;

    byte[] input;

    HashFunction sha1, md5;

    @Setup
    public void setup() {
        input = RandomStringUtils.random(hashSize).getBytes();

        sha1 = Hashing.sha1();
        md5 = Hashing.md5();
    }

    @Benchmark
    public void sha1(Blackhole bh) {
        bh.consume(sha1.hashBytes(input));
        bh.consume(putStuff(sha1.defaultHasher()).hash());
        bh.consume(putStuff(sha1.primitiveHasher()).hash());
    }

    @Benchmark
    public void md5(Blackhole bh) {
        bh.consume(md5.hashBytes(input));
        bh.consume(putStuff(md5.defaultHasher()).hash());
        bh.consume(putStuff(md5.primitiveHasher()).hash());
    }

    private Hasher putStuff(Hasher hasher) {
        putStuff((PrimitiveHasher) hasher);
        hasher.putNull();
        return hasher;
    }

    private PrimitiveHasher putStuff(PrimitiveHasher hasher) {
        hasher.putBoolean(true);
        hasher.putDouble(1D);
        hasher.putInt(1);
        hasher.putInt(Integer.MAX_VALUE);
        hasher.putLong(1L);
        hasher.putLong(Long.MAX_VALUE);
        hasher.putByte((byte) 1);
        hasher.putBytes(DUMMY_BYTES);
        hasher.putHash(DUMMY_HASH);
        hasher.putString(DUMMY_STRING);
        return hasher;
    }
}
