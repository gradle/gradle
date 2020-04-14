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

import org.gradle.api.GradleException;
import org.gradle.api.internal.file.archive.ZipCopyAction;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.GFileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ServiceScope(ServiceScope.Value.UserHome)
public class ClasspathBuilder {
    private static final int BUFFER_SIZE = 8192;

    public void jar(File jarFile, Action action) {
        try {
            buildJar(jarFile, action);
        } catch (Exception e) {
            throw new GradleException(String.format("Failed to create %s.", jarFile), e);
        }
    }

    private void buildJar(File jarFile, Action action) throws IOException {
        File tmpFile = tempFileFor(jarFile);
        try {
            try (ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile), BUFFER_SIZE))) {
                outputStream.setLevel(0);
                action.execute(new ZipEntryBuilder(outputStream));
            }
            Files.deleteIfExists(jarFile.toPath());
            GFileUtils.moveFile(tmpFile, jarFile);
        } finally {
            Files.deleteIfExists(tmpFile.toPath());
        }
    }

    private File tempFileFor(File outputJar) throws IOException {
        return File.createTempFile(outputJar.getName(), ".tmp");
    }

    public interface Action {
        void execute(EntryBuilder builder) throws IOException;
    }

    public interface EntryBuilder {
        void put(String name, byte[] content) throws IOException;
    }

    private static class ZipEntryBuilder implements EntryBuilder {
        private final ZipOutputStream outputStream;

        public ZipEntryBuilder(ZipOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void put(String name, byte[] content) throws IOException {
            ZipEntry zipEntry = newZipEntryWithFixedTime(name);
            outputStream.putNextEntry(zipEntry);
            outputStream.write(content);
            outputStream.closeEntry();
        }

        private ZipEntry newZipEntryWithFixedTime(String name) {
            ZipEntry entry = new ZipEntry(name);
            entry.setTime(ZipCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES);
            return entry;
        }
    }
}
