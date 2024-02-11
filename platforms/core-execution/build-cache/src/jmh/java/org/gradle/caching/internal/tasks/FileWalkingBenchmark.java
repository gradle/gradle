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

import org.apache.commons.io.FileUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@State(Scope.Benchmark)
public class FileWalkingBenchmark {
    Path tempDirPath;
    Path missingPath;
    Path existingPath;
    File tempDirFile;
    File missingFile;
    File existingFile;

    @Param({"true", "false"})
    boolean missing;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        this.tempDirPath = Files.createTempDirectory("file-walking");
        this.tempDirFile = tempDirPath.toFile();

        this.missingPath = tempDirPath.resolve("aaa-missing/bbb-missing/ccc-missing/ddd-missing/missing.txt");
        this.missingFile = missingPath.toFile();
        mkdirs(missingPath.getParent());

        this.existingPath = tempDirPath.resolve("aaa/bbb/ccc/ddd/missing.txt");
        this.existingFile = existingPath.toFile();
        mkdirs(existingPath.getParent());
        Files.createFile(existingPath);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        FileUtils.forceDelete(tempDirFile);
    }

    private static void mkdirs(Path path) throws IOException {
        if (path == null || Files.isDirectory(path)) {
            return;
        }
        mkdirs(path.getParent());
        Files.createDirectory(path);
    }

    @Benchmark
    public void java6walk(Blackhole blackhole) {
        File file = missing ? missingFile : existingFile;
        while (!file.equals(tempDirFile)) {
            file = file.getParentFile();
            blackhole.consume(file.exists());
        }
        blackhole.consume(file);
    }

    @Benchmark
    public void java7walk(Blackhole blackhole) {
        Path path = missing ? missingPath : existingPath;
        while (!path.equals(tempDirPath)) {
            path = path.getParent();
            blackhole.consume(Files.exists(path));
        }
        blackhole.consume(path);
    }

    @Benchmark
    public void java6exists(Blackhole blackhole) {
        File file = missing ? missingFile : existingFile;
        blackhole.consume(file.exists());
    }

    @Benchmark
    public void java7exists(Blackhole blackhole) {
        Path path = missing ? missingPath : existingPath;
        blackhole.consume(Files.exists(path));
    }
}
