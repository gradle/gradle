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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.security.MessageDigest;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark                                         Mode  Cnt          Score          Error  Units
 * MessageDigestThreadingBenchmark.md5_clone        thrpt    5   52338355.375 ±   153545.495  ops/s
 * MessageDigestThreadingBenchmark.md5_threadLocal  thrpt    5  890364532.949 ± 11466693.846  ops/s
 * MessageDigestThreadingBenchmark.sha1_clone       thrpt    5   27785773.663 ±   147045.446  ops/s
 * MessageDigestThreadingBenchmark.sha1_threadLocal thrpt    5  891209465.144 ±  7541321.105  ops/s
 **/
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class MessageDigestThreadingBenchmark {
    MessageDigest md5, sha1;
    ThreadLocal<MessageDigest> md5_TL, sha1_TL;

    @Setup
    public void setup() throws Exception {
        md5 = MessageDigest.getInstance("MD5");
        md5_TL = ThreadLocal.withInitial(new Supplier<MessageDigest>() {
            public MessageDigest get() {
                try {
                    return (MessageDigest) md5.clone();
                } catch (CloneNotSupportedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        sha1 = MessageDigest.getInstance("SHA-1");
        sha1_TL = ThreadLocal.withInitial(new Supplier<MessageDigest>() {
            public MessageDigest get() {
                try {
                    return (MessageDigest) sha1.clone();
                } catch (CloneNotSupportedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    @Benchmark
    public Object md5_clone() throws Exception {
        return md5.clone();
    }

    @Benchmark
    public Object md5_threadLocal() {
        return md5_TL.get();
    }

    @Benchmark
    public Object sha1_clone() throws Exception {
        return sha1.clone();
    }

    @Benchmark
    public Object sha1_threadLocal() {
        return sha1_TL.get();
    }
}
