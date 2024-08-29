/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.tools.api;

import com.google.common.io.ByteStreams;
import org.gradle.internal.tools.api.impl.JavaApiMemberWriter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.concurrent.TimeUnit.SECONDS;

@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@State(Scope.Benchmark)
public class ApiClassExtractorBenchmark {

    @State(Scope.Thread)
    public static class BenchmarkState {
        ApiClassExtractor apiClassExtractor;
        List<byte[]> classpath;
        AtomicLong byteCount = new AtomicLong(0);

        @Setup(Level.Trial)
        public void setup() {
            this.apiClassExtractor = ApiClassExtractor.withWriter(JavaApiMemberWriter.adapter())
                .includePackagePrivateMembers()
                .build();

//            List<Path> paths = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
//
//                .map(Paths::get)
//                .collect(Collectors.toList());
            List<Path> paths = Arrays.asList(Paths.get("/Users/lptr/.gradle/caches/8.9/generated-gradle-jars/gradle-api-8.9.jar"));

            System.out.println("Processing " + paths.size() + " classpath entries: " + paths + " with total size of " + NumberFormat.getNumberInstance(Locale.ROOT).format(paths.stream().map(Path::toFile).mapToLong(File::length).sum()) + " bytes");

            this.classpath = paths.stream()
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .map(BenchmarkState::readAllBytes)
                .collect(Collectors.toList());
        }

        @Setup(Level.Iteration)
        public void setupIteration() {
            byteCount.set(0);
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            System.out.println("Processed " + NumberFormat.getNumberInstance(Locale.ROOT).format(byteCount.get()) + " bytes");
        }

        private static byte[] readAllBytes(Path path) {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void extractionBenchmarkUsingZipStream(BenchmarkState state, Blackhole blackhole) {
        state.classpath.forEach(bytes -> {
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                    while (true) {
                        ZipEntry entry = zip.getNextEntry();
                        if (entry == null) {
                            break;
                        }
                        if (!entry.getName().endsWith(".class")) {
                            continue;
                        }
                        readClass(entry.getName(), zip, state, blackhole);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        );
    }

    private static void readClass(String name, InputStream zip, BenchmarkState state, Blackhole blackhole) {
        try {
            byte[] classBytes = ByteStreams.toByteArray(zip);
            Optional<byte[]> result = state.apiClassExtractor.extractApiClassFrom(classBytes);
            blackhole.consume(result);
            state.byteCount.addAndGet(classBytes.length);
        } catch (IllegalArgumentException e) {
            // Ignore
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract API class from " + name, e);
        }
    }
}
