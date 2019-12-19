/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.runtimeshaded;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class SourcesJarCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourcesJarCreator.class);

    private final ProgressLoggerFactory progressLoggerFactory;

    public SourcesJarCreator(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public void create(File outputJar, File sourcesDir) {
        LOGGER.info("Generating " + outputJar.getAbsolutePath());

        ProgressLogger progressLogger = progressLoggerFactory.newOperation(SourcesJarCreator.class);
        progressLogger.setDescription("Generating " + outputJar.getName());
        progressLogger.started();

        try {
            createSourcesJar(outputJar, sourcesDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            progressLogger.completed();
        }
    }

    private static void createSourcesJar(File outputJar, Path sourcesDir) throws IOException {
        File tmpFile = tempFileFor(outputJar);

        try (ZipOutputStream jarOutputStream = openJarOutputStream(tmpFile)) {
            Files.walk(sourcesDir).forEach(path -> {
                try {
                    writeZipEntry(path, sourcesDir, jarOutputStream);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            jarOutputStream.finish();
        }

        Files.move(tmpFile.toPath(), outputJar.toPath());
    }

    private static void writeZipEntry(Path sourcePath, Path sourcesDir, ZipOutputStream outputStream) throws IOException {
        if (Files.isDirectory(sourcePath)) {
            outputStream.putNextEntry(new ZipEntry(sourcesDir.relativize(sourcePath) + File.separator));
        } else {
            ZipEntry zipEntry = new ZipEntry(sourcesDir.relativize(sourcePath).toString());
            outputStream.putNextEntry(zipEntry);
            Files.copy(sourcePath, outputStream);
        }
        outputStream.closeEntry();
    }

    private static File tempFileFor(File outputJar) throws IOException {
        File tmpFile = File.createTempFile(outputJar.getName(), ".tmp");
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    private static ZipOutputStream openJarOutputStream(File outputJar) throws IOException {
        ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(outputJar.toPath()));
        outputStream.setLevel(0);
        return outputStream;
    }
}
