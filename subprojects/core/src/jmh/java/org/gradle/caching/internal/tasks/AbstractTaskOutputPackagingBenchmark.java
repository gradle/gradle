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

package org.gradle.caching.internal.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Warmup(iterations = 3)
@Measurement(iterations = 7)
@State(Scope.Benchmark)
public abstract class AbstractTaskOutputPackagingBenchmark {
    private static final Map<String, Packer> PACKERS = ImmutableMap.<String, Packer>builder()
        .put("tar.snappy", new SnappyPacker(new TarPacker()))
        .put("tar.gz", new GzipPacker(new TarPacker()))
        .put("tar", new TarPacker())
        .put("zip", new ZipPacker(true))
        .build();

    DataSource sample;

    @Param({"tar.snappy", "tar.gz", "tar", "zip"})
    String format;

    List<DataSource> inputs;
    int fileCount = 273;
    int minFileSize = 273;
    int maxFileSize = 273 * 1024;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        System.out.println(">>> Measuring format: " + format);
        this.inputs = createInputFiles(fileCount, minFileSize, maxFileSize);
        this.sample = packSample("sample." + format, PACKERS.get(format));
    }

    private ImmutableList<DataSource> createInputFiles(int fileCount, int minFileSize, int maxFileSize) throws IOException {
        Random random = new Random(1234L);
        ImmutableList.Builder<DataSource> inputs = ImmutableList.builder();
        for (int idx = 0; idx < fileCount; idx++) {
            String name = "input-" + idx + ".bin";
            int fileSize = minFileSize + random.nextInt(maxFileSize - minFileSize);
            byte[] buffer = new byte[fileSize];
            random.nextBytes(buffer);
            DataSource input = createSource(name, buffer, Level.Trial);
            inputs.add(input);
        }
        return inputs.build();
    }

    private DataSource packSample(String name, Packer packer) throws IOException {
        long sumLength = 0;
        for (DataSource input : inputs) {
            sumLength += input.getLength();
        }
        DataTarget target = createTarget(name, Level.Trial);
        packer.pack(inputs, target);
        DataSource source = target.toSource();
        System.out.printf(">>> %s is %d bytes long (uncompressed length: %d, compression ratio: %,.2f%%)%n", name, source.getLength(), sumLength, (double) source.getLength() / sumLength);
        return source;
    }

    @Benchmark
    public void pack() throws IOException {
        PACKERS.get(format).pack(inputs, createTarget("pack-" + format, Level.Iteration));
    }

    @Benchmark
    public void unpack() throws IOException {
        PACKERS.get(format).unpack(sample, createTargetFactory("unpack-" + format, Level.Iteration));
    }

    protected abstract DataSource createSource(String name, byte[] bytes, Level level) throws IOException;

    protected abstract DataTarget createTarget(String name, Level level);

    protected abstract Packer.DataTargetFactory createTargetFactory(String root, Level level) throws IOException;
}
