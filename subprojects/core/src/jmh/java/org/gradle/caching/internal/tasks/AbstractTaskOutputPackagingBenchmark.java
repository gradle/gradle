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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.List;
import java.util.Random;

@Warmup(iterations = 3)
@Measurement(iterations = 7)
public abstract class AbstractTaskOutputPackagingBenchmark {
    List<DataSource> inputs;
    DataSource sampleTarGz;
    DataSource sampleZip;
    int fileCount = 100;
    int minFileSize = 2 * 1024;
    int maxFileSize = 64 * 1024;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        this.inputs = createInputFiles(fileCount, minFileSize, maxFileSize);
        this.sampleTarGz = packSample("sample.tar.gz", new TarGzipPacker());
        this.sampleZip = packSample("sample.zip", new ZipPacker());
    }

    private ImmutableList<DataSource> createInputFiles(int fileCount, int minFileSize, int maxFileSize) throws IOException {
        Random random = new Random(1234L);
        ImmutableList.Builder<DataSource> inputs = ImmutableList.builder();
        for (int idx = 0; idx < fileCount; idx++) {
            String name = "input-" + idx + ".bin";
            int fileSize = minFileSize + random.nextInt(maxFileSize - minFileSize);
            byte[] buffer = new byte[fileSize];
            random.nextBytes(buffer);
            DataSource input = createSource(name, buffer);
            inputs.add(input);
        }
        return inputs.build();
    }

    protected abstract DataSource createSource(String name, byte[] bytes) throws IOException;

    private DataSource packSample(String name, Packer packer) throws IOException {
        DataTarget target = createTarget(name);
        packer.pack(inputs, target);
        return target.toSource();
    }

    protected abstract DataTarget createTarget(String name);

    @Benchmark
    public void packTarGz(Blackhole bh) throws IOException {
        new TarGzipPacker().pack(inputs, createTarget("pack-tar-gz"));
    }

    @Benchmark
    public void packJavaZip(Blackhole bh) throws IOException {
        new ZipPacker().pack(inputs, createTarget("pack-zip"));
    }

    @Benchmark
    public void unpackTarGz(Blackhole bh) throws IOException, InterruptedException {
        new TarGzipPacker().unpack(sampleTarGz, createTargetFactory("unpack-tar-gz"));
    }

    @Benchmark
    public void unpackJavaZip(Blackhole bh) throws IOException {
        new ZipPacker().unpack(sampleZip, createTargetFactory("unpack-zip"));
    }

    protected abstract Packer.DataTargetFactory createTargetFactory(String root) throws IOException;
}
