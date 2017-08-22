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

import com.google.common.collect.ImmutableMap;
import org.bouncycastle.jcajce.provider.digest.Blake2b;
import org.bouncycastle.jcajce.provider.digest.MD5;
import org.bouncycastle.jcajce.provider.digest.SHA1;
import org.gradle.internal.Factory;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;

@Fork(1)
@Threads(4)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class HashingAlgorithmsBenchmark {

    private static MessageDigest getDigest(String name) {
        try {
            return MessageDigest.getInstance(name);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError();
        }
    }

    static Map<String, MessageDigest> DIGESTS = ImmutableMap.<String, MessageDigest>builder()
        .put("md5.java", getDigest("MD5"))
        .put("md5.bc", new MD5.Digest())
        .put("sha1.java", getDigest("SHA-1"))
        .put("sha1.bc", new SHA1.Digest())
        .put("blake2b", new Blake2b.Blake2b160())
        .build();

    Random random = new Random(1234L);

    @Param({"16", "1024", "65536"})
    int hashSize;

    @Param({"md5.java", "md5.bc", "sha1.java", "sha1.bc", "blake2b"})
    String type;

    byte[] input;
    Factory<MessageDigest> digestFactory;

    @Setup(Level.Iteration)
    public void setup() throws CloneNotSupportedException {
        input = new byte[hashSize];
        random.nextBytes(input);
        digestFactory = new Factory<MessageDigest>() {
            @Override
            public MessageDigest create() {
                try {
                    return (MessageDigest) DIGESTS.get(type).clone();
                } catch (CloneNotSupportedException e) {
                    throw new AssertionError();
                }
            }
        };
    }

    @Benchmark
    public void measure(Blackhole blackhole) {
        MessageDigest digest = digestFactory.create();
        digest.update(input);
        byte[] hash = digest.digest();
        blackhole.consume(hash);
    }
}
