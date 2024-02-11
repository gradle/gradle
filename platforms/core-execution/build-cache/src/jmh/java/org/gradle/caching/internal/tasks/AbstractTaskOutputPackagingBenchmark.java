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
import org.apache.commons.io.FileUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 7)
@State(Scope.Benchmark)
public abstract class AbstractTaskOutputPackagingBenchmark {
    private static final DefaultDirectoryProvider DIRECTORY_PROVIDER = new DefaultDirectoryProvider();

    private static final Map<String, Packer> PACKERS = ImmutableMap.<String, Packer>builder()
        .put("tar.snappy", new SnappyPacker(new CommonsTarPacker(4)))
        .put("tar.snappy.commons", new SnappyCommonsPacker(new CommonsTarPacker(4)))
        .put("tar.snappy.dain", new SnappyDainPacker(new CommonsTarPacker(4)))
        .put("tar.snappy.small", new SnappyPacker(new CommonsTarPacker(2)))
        .put("tar.snappy.large", new SnappyPacker(new CommonsTarPacker(64)))
        .put("tar", new CommonsTarPacker(4))
        .put("tar.commons", new CommonsTarPacker(4))
        .put("tar.jtar", new JTarPacker(4))
        .put("tar.small", new CommonsTarPacker(2))
        .put("tar.large", new CommonsTarPacker(64))
        .put("tar.gz", new GzipPacker(new CommonsTarPacker(4)))
        .put("zip", new ZipPacker(4))
        .build();

    private static final Map<String, DataAccessor> ACCESSORS = ImmutableMap.<String, DataAccessor>builder()
        .put("direct", new DirectFileFileAccessor(DIRECTORY_PROVIDER))
        .put("buffered", new BufferedFileAccessor(8, DIRECTORY_PROVIDER))
        .put("buffered.small", new BufferedFileAccessor(2, DIRECTORY_PROVIDER))
        .put("buffered.large", new BufferedFileAccessor(64, DIRECTORY_PROVIDER))
        .put("in-memory", new InMemoryDataAccessor())
        .build();

    DataSource sample;

    List<DataSource> inputs;
    int fileCount = 273;
    int minFileSize = 273;
    int maxFileSize = 273 * 1024;

    protected abstract String getPackerName();

    protected abstract String getAccessorName();

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        DIRECTORY_PROVIDER.setupTrial();
        String packerName = getPackerName();
        String accessorName = getAccessorName();
        System.out.println(">>> Measuring format: " + packerName + " with accessor " + accessorName);
        Packer packer = PACKERS.get(packerName);
        DataAccessor accessor = ACCESSORS.get(accessorName);
        this.inputs = createInputFiles(fileCount, minFileSize, maxFileSize, accessor);
        this.sample = packSample("sample." + packerName, inputs, packer, accessor);
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() throws IOException {
        DIRECTORY_PROVIDER.tearDownTrial();
    }

    @Setup(Level.Iteration)
    public void setupIteration() throws IOException {
        DIRECTORY_PROVIDER.setupIteration();
    }

    @TearDown(Level.Iteration)
    public void tearDownIteration() throws IOException {
        DIRECTORY_PROVIDER.tearDownIteration();
    }

    private static ImmutableList<DataSource> createInputFiles(int fileCount, int minFileSize, int maxFileSize, DataAccessor accessor) throws IOException {
        Random random = new Random(1234L);
        ImmutableList.Builder<DataSource> inputs = ImmutableList.builder();
        for (int idx = 0; idx < fileCount; idx++) {
            String name = "input-" + idx + ".bin";
            int fileSize = minFileSize + random.nextInt(maxFileSize - minFileSize);
            byte[] buffer = new byte[fileSize];
            random.nextBytes(buffer);
            DataSource input = accessor.createSource(name, buffer, Level.Trial);
            inputs.add(input);
        }
        return inputs.build();
    }

    private static DataSource packSample(String name, List<DataSource> inputs, Packer packer, DataAccessor accessor) throws IOException {
        long sumLength = 0;
        for (DataSource input : inputs) {
            sumLength += input.getLength();
        }
        DataTarget target = accessor.createTarget(name, Level.Trial);
        packer.pack(inputs, target);
        DataSource source = target.toSource();
        System.out.printf(">>> %s is %d bytes long (uncompressed length: %d, compression ratio: %,.2f%%)%n", name, source.getLength(), sumLength, (double) source.getLength() / sumLength);
        return source;
    }

    @Benchmark
    public void pack() throws IOException {
        String packerName = getPackerName();
        String accessorName = getAccessorName();
        Packer packer = PACKERS.get(packerName);
        DataAccessor accessor = ACCESSORS.get(accessorName);
        packer.pack(inputs, accessor.createTarget("pack-" + packerName, Level.Iteration));
    }

    @Benchmark
    public void unpack() throws IOException {
        String packerName = getPackerName();
        String accessorName = getAccessorName();
        Packer packer = PACKERS.get(packerName);
        DataAccessor accessor = ACCESSORS.get(accessorName);
        packer.unpack(sample, accessor.createTargetFactory("unpack-" + accessorName, Level.Iteration));
    }

    private static class DefaultDirectoryProvider implements DirectoryProvider {
        private Path tempDir;
        private Path iterationDir;

        @Setup(Level.Trial)
        public void setupTrial() throws IOException {
            this.tempDir = Files.createTempDirectory("task-output-cache-benchmark-");
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() throws IOException {
            FileUtils.forceDelete(tempDir.toFile());
        }

        @Setup(Level.Iteration)
        public void setupIteration() throws IOException {
            this.iterationDir = Files.createTempDirectory(tempDir, "iteration-");
        }

        @TearDown(Level.Iteration)
        public void tearDownIteration() throws IOException {
            FileUtils.forceDelete(iterationDir.toFile());
        }

        @Override
        public Path getRoot(Level level) {
            switch (level) {
                case Trial:
                    return tempDir;
                case Iteration:
                    return iterationDir;
                default:
                    throw new AssertionError();
            }
        }
    }
}
