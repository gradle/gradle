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
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.bouncycastle.jcajce.provider.digest.Blake2b;
import org.bouncycastle.jcajce.provider.digest.MD5;
import org.bouncycastle.jcajce.provider.digest.SHA1;
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

    static final Map<String, HashProcessorFactory> HASHERS = ImmutableMap.<String, HashProcessorFactory>builder()
        .put("md5.java", new MessageDigestHashProcessorFactory(getDigest("MD5")))
        .put("md5.bc", new MessageDigestHashProcessorFactory(new MD5.Digest()))
        .put("sha1.java", new MessageDigestHashProcessorFactory(getDigest("SHA-1")))
        .put("sha1.bc", new MessageDigestHashProcessorFactory(new SHA1.Digest()))
        .put("blake2b.bc", new MessageDigestHashProcessorFactory(new Blake2b.Blake2b160()))
        .put("murmur3.guava", new GuavaProcessorFactory(Hashing.murmur3_128()))
        .build();

    Random random = new Random(1234L);

    @Param({"16", "1024", "65536"})
    int hashSize;

    // @Param({"md5.java", "md5.bc", "sha1.java", "sha1.bc", "blake2b.bc"})
    @Param({"md5.java", "murmur3.guava"})
    String type;

    byte[] input;
    HashProcessorFactory processorFactory;

    @Setup(Level.Iteration)
    public void setup() throws CloneNotSupportedException {
        input = new byte[hashSize];
        random.nextBytes(input);
        processorFactory = HASHERS.get(type);
    }

    @Benchmark
    public void measure(Blackhole blackhole) {
        HashProcessor processor = processorFactory.create();
        processor.process(input, blackhole);
    }

    interface HashProcessor {
        void process(byte[] input, Blackhole blackhole);
    }

    interface HashProcessorFactory {
        HashProcessor create();
    }

    private static class MessageDigestHashProcessorFactory implements HashProcessorFactory {
        private final MessageDigest baseDigest;

        public MessageDigestHashProcessorFactory(MessageDigest baseDigest) {
            this.baseDigest = baseDigest;
        }

        @Override
        public HashProcessor create() {
            try {
                return new MessageDigestProcessor((MessageDigest) baseDigest.clone());
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static class MessageDigestProcessor implements HashProcessor {
        private final MessageDigest digest;

        public MessageDigestProcessor(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void process(byte[] input, Blackhole blackhole) {
            digest.update(input);
            byte[] hash = digest.digest();
            blackhole.consume(hash);
        }
    }

    private static class GuavaProcessorFactory implements HashProcessorFactory {
        private final HashFunction hashFunction;

        public GuavaProcessorFactory(HashFunction hashFunction) {
            this.hashFunction = hashFunction;
        }

        @Override
        public HashProcessor create() {
            return new GuavaProcessor(hashFunction.newHasher());
        }
    }

    private static class GuavaProcessor implements HashProcessor {
        private final Hasher hasher;

        public GuavaProcessor(Hasher hasher) {
            this.hasher = hasher;
        }

        @Override
        public void process(byte[] input, Blackhole blackhole) {
            hasher.putBytes(input);
            blackhole.consume(hasher.hash());
        }
    }
}
