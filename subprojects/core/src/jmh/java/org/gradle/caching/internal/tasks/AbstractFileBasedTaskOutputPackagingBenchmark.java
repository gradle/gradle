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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@SuppressWarnings("Since15")
public abstract class AbstractFileBasedTaskOutputPackagingBenchmark extends AbstractTaskOutputPackagingBenchmark {
    private Path tempDir;
    private Path iterationDir;

    @Setup(Level.Trial)
    public void setupFiles() throws IOException {
        this.tempDir = Files.createTempDirectory("task-output-cache-benchmark-");
    }

    @TearDown(Level.Trial)
    public void destroyFiles() throws IOException {
        FileUtils.forceDelete(tempDir.toFile());
    }

    @Setup(Level.Iteration)
    public void setupIterationFiles() throws IOException {
        this.iterationDir = Files.createTempDirectory(tempDir, "iteration-");
    }

    @TearDown(Level.Iteration)
    public void destroyIterationFiles() throws IOException {
        FileUtils.forceDelete(iterationDir.toFile());
    }

    @Override
    protected DataSource createSource(String name, byte[] bytes, Level level) throws IOException {
        Path path = getPath(name, level);
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        return new Source(path);
    }

    @Override
    protected DataTarget createTarget(String name, Level level) {
        return new Target(getPath(name, level));
    }

    private Path getPath(String name, Level level) {
        Path root;
        switch (level) {
            case Trial:
                root = tempDir;
                break;
            case Iteration:
                root = iterationDir;
                break;
            default:
                throw new AssertionError();
        }
        return root.resolve(name);
    }

    @Override
    protected Packer.DataTargetFactory createTargetFactory(final String root, Level level) throws IOException {
        final Path rootDir;
        switch (level) {
            case Trial:
                rootDir = Files.createTempDirectory(tempDir, root);
                break;
            case Iteration:
                rootDir = Files.createTempDirectory(iterationDir, root);
                break;
            default:
                throw new AssertionError();
        }
        return new Packer.DataTargetFactory() {
            @Override
            public DataTarget createDataTarget(String name) {
                return new Target(rootDir.resolve(name));
            }
        };
    }

    protected abstract InputStream openInput(Path path) throws IOException;

    protected abstract OutputStream openOutput(Path path) throws IOException;

    private class Source implements DataSource {
        private final Path path;

        public Source(Path path) {
            this.path = path;
        }

        @Override
        public String getName() {
            return path.getFileName().toString();
        }

        @Override
        public InputStream openInput() throws IOException {
            return AbstractFileBasedTaskOutputPackagingBenchmark.this.openInput(path);
        }

        @Override
        public long getLength() throws IOException {
            return Files.size(path);
        }
    }

    private class Target implements DataTarget {
        private final Path path;

        public Target(Path path) {
            this.path = path;
        }

        @Override
        public String getName() {
            return path.getFileName().toString();
        }

        @Override
        public OutputStream openOutput() throws IOException {
            return AbstractFileBasedTaskOutputPackagingBenchmark.this.openOutput(path);
        }

        @Override
        public DataSource toSource() throws IOException {
            return new Source(path);
        }
    }
}
