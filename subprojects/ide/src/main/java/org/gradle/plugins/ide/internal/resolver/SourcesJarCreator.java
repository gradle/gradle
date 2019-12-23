/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SourcesJarCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourcesJarCreator.class);

    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public SourcesJarCreator(DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    public void create(File outputJar, File sourcesDir) {
        LOGGER.info("Generating " + outputJar.getAbsolutePath());

        try {
            createSourcesJar(outputJar, sourcesDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void createSourcesJar(File outputJar, File sourcesDir) throws IOException {
        File tmpFile = tempFileFor(outputJar);

        List<FileTreeElement> filesToZip = getFilesToZip(sourcesDir);
        try (ZipOutputStream jarOutputStream = openJarOutputStream(tmpFile)) {
            for (FileTreeElement file : filesToZip) {
                writeZipEntry(file, jarOutputStream);
            }
            jarOutputStream.finish();
        }

        Files.move(tmpFile.toPath(), outputJar.toPath());
    }

    private List<FileTreeElement> getFilesToZip(File root) {
        List<FileTreeElement> filesToZip = new ArrayList<>();
        directoryFileTreeFactory.create(root).visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                filesToZip.add(fileDetails);
            }
        });
        filesToZip.sort(Comparator.comparing(FileTreeElement::getPath));
        return filesToZip;
    }

    private static void writeZipEntry(FileTreeElement source, ZipOutputStream outputStream) throws IOException {
        ZipEntry zipEntry = new ZipEntry(source.getPath());
        outputStream.putNextEntry(zipEntry);
        source.copyTo(outputStream);
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
