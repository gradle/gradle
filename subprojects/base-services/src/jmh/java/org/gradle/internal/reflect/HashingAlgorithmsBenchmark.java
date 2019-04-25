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

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.apache.commons.lang.RandomStringUtils;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.jcajce.provider.digest.MD5;
import org.bouncycastle.jcajce.provider.digest.SHA1;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.security.MessageDigest;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * https://preshing.com/20110504/hash-collision-probabilities
 * https://github.com/rurban/smhasher
 * https://en.wikipedia.org/wiki/Hash_function_security_summary
 *
 * Benchmark                                                        (hashSize)   Mode  Cnt          Score          Error  Units
 * HashingAlgorithmsBenchmark.blake2b_128_bc                              1024  thrpt    5     392412.167 ±     7840.282  ops/s
 * HashingAlgorithmsBenchmark.blake2b_128_bc                             65536  thrpt    5       6372.976 ±     1077.810  ops/s
 * HashingAlgorithmsBenchmark.blake2b_128_bc                          67108864  thrpt    5          6.201 ±        0.606  ops/s
 * HashingAlgorithmsBenchmark.md5_bc                                      1024  thrpt    5     408892.744 ±     2075.719  ops/s
 * HashingAlgorithmsBenchmark.md5_bc                                     65536  thrpt    5       6629.605 ±       61.378  ops/s
 * HashingAlgorithmsBenchmark.md5_bc                                  67108864  thrpt    5          6.375 ±        0.033  ops/s
 * HashingAlgorithmsBenchmark.md5_guava                                   1024  thrpt    5     479881.416 ±     5646.316  ops/s
 * HashingAlgorithmsBenchmark.md5_guava                                  65536  thrpt    5       8126.245 ±       73.154  ops/s
 * HashingAlgorithmsBenchmark.md5_guava                               67108864  thrpt    5          8.016 ±        0.083  ops/s
 * HashingAlgorithmsBenchmark.md5_java                                    1024  thrpt    5     486863.854 ±     6423.621  ops/s
 * HashingAlgorithmsBenchmark.md5_java                                   65536  thrpt    5       8169.438 ±       90.108  ops/s
 * HashingAlgorithmsBenchmark.md5_java                                67108864  thrpt    5          8.005 ±        0.109  ops/s
 * HashingAlgorithmsBenchmark.murmur3_128_guava_non_cryptographic         1024  thrpt    5    3599095.009 ±    30686.631  ops/s
 * HashingAlgorithmsBenchmark.murmur3_128_guava_non_cryptographic        65536  thrpt    5      82838.124 ±      833.338  ops/s
 * HashingAlgorithmsBenchmark.murmur3_128_guava_non_cryptographic     67108864  thrpt    5         87.683 ±        2.072  ops/s
 * HashingAlgorithmsBenchmark.sha1_bc                                     1024  thrpt    5     286221.423 ±      880.325  ops/s
 * HashingAlgorithmsBenchmark.sha1_bc                                    65536  thrpt    5       4654.420 ±       42.925  ops/s
 * HashingAlgorithmsBenchmark.sha1_bc                                 67108864  thrpt    5          4.754 ±        0.021  ops/s
 * HashingAlgorithmsBenchmark.sha1_java                                   1024  thrpt    5     346946.993 ±     3125.038  ops/s
 * HashingAlgorithmsBenchmark.sha1_java                                  65536  thrpt    5       5792.155 ±       53.079  ops/s
 * HashingAlgorithmsBenchmark.sha1_java                               67108864  thrpt    5          5.671 ±        0.045  ops/s
 * HashingAlgorithmsBenchmark.sha3_224_bc                                 1024  thrpt    5     311901.853 ±     4168.865  ops/s
 * HashingAlgorithmsBenchmark.sha3_224_bc                                65536  thrpt    5       5365.516 ±      309.016  ops/s
 * HashingAlgorithmsBenchmark.sha3_224_bc                             67108864  thrpt    5          5.324 ±        0.047  ops/s
 * HashingAlgorithmsBenchmark.sipHash24_64_guava_non_cryptographic        1024  thrpt    5    1485656.535 ±    38410.281  ops/s
 * HashingAlgorithmsBenchmark.sipHash24_64_guava_non_cryptographic       65536  thrpt    5      26365.336 ±      118.140  ops/s
 * HashingAlgorithmsBenchmark.sipHash24_64_guava_non_cryptographic    67108864  thrpt    5         24.639 ±        1.265  ops/s
 **/
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class HashingAlgorithmsBenchmark {
    @Param({"1024", "65536", "67108864"})
    int hashSize;

    byte[] input;

    MessageDigest md5_java;
    MD5.Digest md5_bc;
    HashFunction md5_guava;

    MessageDigest sha1_java;
    SHA1.Digest sha1_bc;
    SHA3.Digest224 sha3_224_bc;

    Blake2bDigest blake2b_128_bc;

    HashFunction murmur3_128_guava;

    HashFunction sipHash24_64_guava;

    @Setup
    public void setup() throws Exception {
        input = RandomStringUtils.random(hashSize).getBytes();

        md5_java = MessageDigest.getInstance("MD5");
        md5_bc = new MD5.Digest();
        md5_guava = Hashing.md5();

        sha1_java = MessageDigest.getInstance("SHA-1");
        sha1_bc = new SHA1.Digest();
        sha3_224_bc = new SHA3.Digest224();

        blake2b_128_bc = new Blake2bDigest(128);

        murmur3_128_guava = Hashing.murmur3_128();

        sipHash24_64_guava = Hashing.sipHash24();
    }

    @Benchmark
    public byte[] md5_java() {
        md5_java.update(input);
        return md5_java.digest();
    }

    @Benchmark
    public byte[] md5_bc() {
        md5_bc.update(input);
        return md5_bc.digest();
    }

    @Benchmark
    public byte[] md5_guava() {
        Hasher hasher = md5_guava.newHasher(input.length);
        hasher.putBytes(input);
        return hasher.hash().asBytes();
    }

    @Benchmark
    public byte[] sha1_java() {
        sha1_java.update(input);
        return sha1_java.digest();
    }

    @Benchmark
    public byte[] sha1_bc() {
        sha1_bc.update(input);
        return sha1_bc.digest();
    }

    @Benchmark
    public byte[] sha3_224_bc() {
        sha3_224_bc.update(input);
        return sha3_224_bc.digest();
    }

    @Benchmark
    public byte[] blake2b_128_bc() {
        blake2b_128_bc.update(input, 0, input.length);
        byte[] hash = new byte[128];
        blake2b_128_bc.doFinal(hash, 0);
        return hash;
    }

    @Benchmark
    public byte[] murmur3_128_guava_non_cryptographic() {
        Hasher hasher = murmur3_128_guava.newHasher();
        hasher.putBytes(input);
        return hasher.hash().asBytes();
    }

    @Benchmark
    public byte[] sipHash24_64_guava_non_cryptographic() {
        Hasher hasher = sipHash24_64_guava.newHasher();
        hasher.putBytes(input);
        return hasher.hash().asBytes();
    }
}
