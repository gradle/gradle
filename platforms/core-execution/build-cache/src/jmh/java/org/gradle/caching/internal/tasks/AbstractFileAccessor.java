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

import org.openjdk.jmh.annotations.Level;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public abstract class AbstractFileAccessor implements DataAccessor {
    private final DirectoryProvider directoryProvider;

    public AbstractFileAccessor(DirectoryProvider directoryProvider) {
        this.directoryProvider = directoryProvider;
    }

    @Override
    public DataSource createSource(String name, byte[] bytes, Level level) throws IOException {
        Path path = getPath(name, level);
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        return new Source(path);
    }

    @Override
    public DataTarget createTarget(String name, Level level) {
        return new Target(getPath(name, level));
    }

    private Path getPath(String name, Level level) {
        return directoryProvider.getRoot(level).resolve(name);
    }

    @Override
    public DataTargetFactory createTargetFactory(final String root, Level level) throws IOException {
        final Path rootDir = Files.createTempDirectory(directoryProvider.getRoot(level), root);
        return new DataTargetFactory() {
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
            return AbstractFileAccessor.this.openInput(path);
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
            return AbstractFileAccessor.this.openOutput(path);
        }

        @Override
        public DataSource toSource() throws IOException {
            return new Source(path);
        }
    }
}
