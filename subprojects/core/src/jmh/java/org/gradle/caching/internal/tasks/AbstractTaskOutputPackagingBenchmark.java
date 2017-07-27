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
import io.airlift.compress.lz4.Lz4Codec;
import io.airlift.compress.lzo.LzoCodec;
import io.airlift.compress.snappy.SnappyCodec;
import org.apache.hadoop.io.compress.CompressionCodec;
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
    CompressionCodec lz4Codec = new Lz4Codec();
    CompressionCodec lzoCodec = new LzoCodec();
    CompressionCodec snappyCodec = new SnappyCodec();
    List<DataSource> inputs;
    // DataSource sampleTarGz;
    DataSource sampleTarLz4;
    DataSource sampleTarLzo;
    DataSource sampleTarSnappy;
    DataSource sampleZip;
    int fileCount = 100;
    int minFileSize = 2 * 1024;
    int maxFileSize = 64 * 1024;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        this.inputs = createInputFiles(fileCount, minFileSize, maxFileSize);
        // this.sampleTarGz = packSample("sample.tar.gz", new TarGzipPacker());
        this.sampleTarLz4 = packSample("sample.tar.lz4", new TarCodecPacker(this.lz4Codec));
        this.sampleTarLzo = packSample("sample.tar.lzo", new TarCodecPacker(lzoCodec));
        this.sampleTarSnappy = packSample("sample.tar.snappy", new TarCodecPacker(snappyCodec));
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
        long sumLength = 0;
        for (DataSource input : inputs) {
            sumLength += input.getLength();
        }
        DataTarget target = createTarget(name);
        packer.pack(inputs, target);
        DataSource source = target.toSource();
        System.out.printf(">>> %s is %d bytes long (uncompressed length: %d, compression ratio: %,.2f%%)%n", name, source.getLength(), sumLength, (double) source.getLength() / sumLength);
        return source;
    }

    protected abstract DataTarget createTarget(String name);

//    @Benchmark
//    public void packTarGz(Blackhole bh) throws IOException {
//        new TarGzipPacker().pack(inputs, createTarget("pack-tar-gz"));
//    }

    @Benchmark
    public void packTarLz4(Blackhole bh) throws IOException {
        new TarCodecPacker(lz4Codec).pack(inputs, createTarget("pack-tar-gz"));
    }

    @Benchmark
    public void packTarLzo(Blackhole bh) throws IOException {
        new TarCodecPacker(lzoCodec).pack(inputs, createTarget("pack-tar-gz"));
    }

    @Benchmark
    public void packTarSnappy(Blackhole bh) throws IOException {
        new TarCodecPacker(snappyCodec).pack(inputs, createTarget("pack-tar-gz"));
    }

    @Benchmark
    public void packZip(Blackhole bh) throws IOException {
        new ZipPacker().pack(inputs, createTarget("pack-zip"));
    }

//    @Benchmark
//    public void unpackTarGz(Blackhole bh) throws IOException, InterruptedException {
//        new TarGzipPacker().unpack(sampleTarGz, createTargetFactory("unpack-tar-gz"));
//    }

    @Benchmark
    public void unpackTarLz4(Blackhole bh) throws IOException, InterruptedException {
        new TarCodecPacker(lz4Codec).unpack(sampleTarLz4, createTargetFactory("unpack-lz4"));
    }

    @Benchmark
    public void unpackTarLzo(Blackhole bh) throws IOException, InterruptedException {
        new TarCodecPacker(lzoCodec).unpack(sampleTarLzo, createTargetFactory("unpack-lzo"));
    }

    @Benchmark
    public void unpackTarSnappy(Blackhole bh) throws IOException, InterruptedException {
        new TarCodecPacker(snappyCodec).unpack(sampleTarSnappy, createTargetFactory("unpack-snappy"));
    }

    @Benchmark
    public void unpackZip(Blackhole bh) throws IOException {
        new ZipPacker().unpack(sampleZip, createTargetFactory("unpack-zip"));
    }

    protected abstract Packer.DataTargetFactory createTargetFactory(String root) throws IOException;
}
