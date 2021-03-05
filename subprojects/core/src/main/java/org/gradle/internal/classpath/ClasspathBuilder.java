/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.file.archive.ZipCopyAction;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

@ServiceScope(Scopes.UserHome.class)
public class ClasspathBuilder {
    private static final int BUFFER_SIZE = 8192;
    private final TemporaryFileProvider temporaryFileProvider;

    @Inject
    ClasspathBuilder(final TemporaryFileProvider temporaryFileProvider) {
        this.temporaryFileProvider = temporaryFileProvider;
    }

    /**
     * Creates a Jar file using the given action to add entries to the file. If the file already exists it will be replaced.
     */
    public void jar(File jarFile, Action action) {
        try {
            buildJar(jarFile, action);
        } catch (Exception e) {
            throw new GradleException(String.format("Failed to create Jar file %s.", jarFile), e);
        }
    }

    private void buildJar(File jarFile, Action action) throws IOException {
        File parentDir = jarFile.getParentFile();
        File tmpFile = temporaryFileProvider.createTemporaryFile(jarFile.getName(), ".tmp");
        try {
            Files.createDirectories(parentDir.toPath());
            try (ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile), BUFFER_SIZE))) {
                outputStream.setLevel(0);
                action.execute(new ZipEntryBuilder(outputStream));
            }
            Files.move(tmpFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpFile.toPath());
        }
    }

    public interface Action {
        void execute(EntryBuilder builder) throws IOException;
    }

    public interface EntryBuilder {
        void put(String name, byte[] content) throws IOException;
    }

    private static class ZipEntryBuilder implements EntryBuilder {
        private final ZipOutputStream outputStream;
        private final Set<String> dirs = new HashSet<>();

        public ZipEntryBuilder(ZipOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void put(String name, byte[] content) throws IOException {
            maybeAddParent(name);
            ZipEntry zipEntry = newZipEntryWithFixedTime(name);
            outputStream.setEncoding("UTF-8");
            outputStream.putNextEntry(zipEntry);
            outputStream.write(content);
            outputStream.closeEntry();
        }

        private void maybeAddParent(String name) throws IOException {
            String dir = dir(name);
            if (dir != null && dirs.add(dir)) {
                maybeAddParent(dir);
                ZipEntry zipEntry = newZipEntryWithFixedTime(dir);
                outputStream.putNextEntry(zipEntry);
                outputStream.closeEntry();
            }
        }

        @Nullable
        String dir(String name) {
            int pos = name.lastIndexOf('/');
            if (pos == name.length() - 1) {
                pos = name.lastIndexOf('/', pos - 1);
            }
            if (pos >= 0) {
                return name.substring(0, pos + 1);
            } else {
                return null;
            }
        }

        private ZipEntry newZipEntryWithFixedTime(String name) {
            ZipEntry entry = new ZipEntry(name);
            entry.setTime(ZipCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES);
            return entry;
        }
    }
}
