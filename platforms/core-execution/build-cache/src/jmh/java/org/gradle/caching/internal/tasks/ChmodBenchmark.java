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
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
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
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@State(Scope.Benchmark)
@SuppressWarnings("OctalInteger")
public class ChmodBenchmark {
    private static final int DEFAULT_JAVA6_FILE_PERMISSIONS = 0644;
    private static final Set<PosixFilePermission> DEFAULT_JAVA7_FILE_PERMISSIONS = EnumSet.of(
        OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ
    );

    private static final int WEIRD_JAVA6_FILE_PERMISSIONS = 0123;
    private static final Set<PosixFilePermission> WEIRD_JAVA7_FILE_PERMISSIONS = EnumSet.of(
        OWNER_EXECUTE, GROUP_WRITE, OTHERS_WRITE, OTHERS_EXECUTE
    );

    Path tempRootDir;
    Path tempDirPath;
    File tempDirFile;
    AtomicInteger counter;
    FileSystem fileSystem;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        this.tempRootDir = Files.createTempDirectory("chmod-benchmark");
        NativeServices.initializeOnDaemon(tempRootDir.toFile());
        this.fileSystem = FileSystems.getDefault();
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() throws IOException {
        FileUtils.forceDelete(tempRootDir.toFile());
    }

    @Setup(Level.Iteration)
    public void setupIteration() throws IOException {
        this.tempDirPath = Files.createTempDirectory(tempRootDir, "iteration");
        this.tempDirFile = tempDirPath.toFile();
        this.counter = new AtomicInteger();
    }

    @TearDown(Level.Iteration)
    public void tearDownIteration() throws IOException {
        FileUtils.forceDelete(tempDirFile);
    }

    @Benchmark
    public void createFileJava6(Blackhole blackhole) throws IOException {
        File file = new File(tempDirFile, "file-" + counter.incrementAndGet());
        boolean created = file.createNewFile();
        blackhole.consume(created);
    }

    @Benchmark
    public void createFileJava7(Blackhole blackhole) throws IOException {
        Path file = Files.createFile(tempDirPath.resolve("file-" + counter.incrementAndGet()));
        blackhole.consume(file);
    }

    @Benchmark
    public void createFileJava6SetDefaultPermission(Blackhole blackhole) throws IOException {
        File file = new File(tempDirFile, "file-" + counter.incrementAndGet());
        boolean created = file.createNewFile();
        fileSystem.chmod(file, 0644);
        blackhole.consume(created);
    }

    @Benchmark
    public void createFileJava7SetDefaultPermission(Blackhole blackhole) throws IOException {
        Path file = Files.createFile(tempDirPath.resolve("file-" + counter.incrementAndGet()));
        Files.setPosixFilePermissions(file, DEFAULT_JAVA7_FILE_PERMISSIONS);
        blackhole.consume(file);
    }

    @Benchmark
    public void createFileJava6SetMixedPermission(Blackhole blackhole) throws IOException {
        int incrementAndGet = counter.incrementAndGet();
        File file = new File(tempDirFile, "file-" + counter.incrementAndGet());
        boolean created = file.createNewFile();
        blackhole.consume(created);
        int permissionsToSet;
        if (incrementAndGet % 2 == 0) {
            permissionsToSet = DEFAULT_JAVA6_FILE_PERMISSIONS;
        } else {
            permissionsToSet = WEIRD_JAVA6_FILE_PERMISSIONS;
        }
        fileSystem.chmod(file, permissionsToSet);
        blackhole.consume(file);
    }

    @Benchmark
    public void createFileJava7SetMixedPermission(Blackhole blackhole) throws IOException {
        int incrementAndGet = counter.incrementAndGet();
        Path file = Files.createFile(tempDirPath.resolve("file-" + incrementAndGet));
        Set<PosixFilePermission> permissionsToSet;
        if (incrementAndGet % 2 == 0) {
            permissionsToSet = DEFAULT_JAVA7_FILE_PERMISSIONS;
        } else {
            permissionsToSet = WEIRD_JAVA7_FILE_PERMISSIONS;
        }
        Files.setPosixFilePermissions(file, permissionsToSet);
        blackhole.consume(file);
    }

    @Benchmark
    public void createFileJava6SetPermissionsWhenNeeded(Blackhole blackhole) throws IOException {
        int incrementAndGet = counter.incrementAndGet();
        File file = new File(tempDirFile, "file-" + counter.incrementAndGet());
        boolean created = file.createNewFile();
        blackhole.consume(created);
        int originalPermissions = fileSystem.getUnixMode(file);
        int permissionsToSet;
        if (incrementAndGet % 2 == 0) {
            permissionsToSet = DEFAULT_JAVA6_FILE_PERMISSIONS;
        } else {
            permissionsToSet = WEIRD_JAVA6_FILE_PERMISSIONS;
        }
        // This should pass 50% of the time
        if (originalPermissions != permissionsToSet) {
            fileSystem.chmod(file, permissionsToSet);
        }
        blackhole.consume(file);
    }

    @Benchmark
    public void createFileJava7SetPermissionsWhenNeeded(Blackhole blackhole) throws IOException {
        int incrementAndGet = counter.incrementAndGet();
        Path file = Files.createFile(tempDirPath.resolve("file-" + incrementAndGet));
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(file);
        Set<PosixFilePermission> permissionsToSet;
        if (incrementAndGet % 2 == 0) {
            permissionsToSet = DEFAULT_JAVA7_FILE_PERMISSIONS;
        } else {
            permissionsToSet = WEIRD_JAVA7_FILE_PERMISSIONS;
        }
        // This should pass 50% of the time
        if (!originalPermissions.equals(permissionsToSet)) {
            Files.setPosixFilePermissions(file, permissionsToSet);
        }
        blackhole.consume(file);
    }
}
