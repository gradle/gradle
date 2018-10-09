/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * On Windows, sometimes we hit `The filename or extension is too long` error when starting a new process. This is because of Windows command's 32KB limitation.
 * In this situation, we merged all classpath entries into one empty `classpath.jar` with `Class-Path:` entry containing all original entries.
 *
 * See <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/downman.html">Adding Classes to the JAR File's Classpath</>
 * And <a href="https://github.com/bazelbuild/bazel/commit/d9a7d3a789be559bd6972208af21adae871d7a44">A similar implementation in Bazel</>
 */
enum ClassPathMerger {
    INSTANCE;
    // Actually 32KB, let's leave some margin
    static final int WINDOWS_CLASSPATH_LENGTH_LIMITATION = 30000;
    // Actually 128KB, let's leave some margin
    static final int UNIX_CLASSPATH_LENGTH_LIMITATION = 120000;

    static final int CLASSPATH_LENGTH_LIMITATION = OperatingSystem.current().isWindows() ? WINDOWS_CLASSPATH_LENGTH_LIMITATION : UNIX_CLASSPATH_LENGTH_LIMITATION;

    List<File> mergeClassPathIfNecessary(List<File> classPath) {
        if (CollectionUtils.join(File.pathSeparator, classPath).length() < CLASSPATH_LENGTH_LIMITATION) {
            return classPath;
        } else {
            return Collections.singletonList(mergedClassPath(classPath));
        }
    }

    private File mergedClassPath(List<File> classPath) {
        try {
            String maneifestContent = generateManifestContent(classPath);
            File jar = jar(maneifestContent);
            return jar;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private File jar(String manifestContent) throws IOException {
        File jar = Files.createTempFile("classpath.jar", null).toFile();
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(jar));

        try {
            ZipEntry entry = new ZipEntry("META-INF/MANIFEST.MF");
            zipOutputStream.putNextEntry(entry);
            ByteStreams.copy(new ByteArrayInputStream(manifestContent.getBytes()), zipOutputStream);
            zipOutputStream.closeEntry();
        } finally {
            IOUtils.closeQuietly(zipOutputStream);
        }

        return jar;
    }

    /**
     * Generate the content of jar MANIFEST.MF file, spaces in path should be escaped since
     * it's the delimiter.
     */
    private String generateManifestContent(List<File> classPath) {
        List<URI> uri = CollectionUtils.collect(classPath, new Transformer<URI, File>() {
            @Override
            public URI transform(File file) {
                return file.toURI();
            }
        });

        return make72Safe("Class-Path: " + CollectionUtils.join(" ", uri) + "\r\n");
    }

    /*
     * This method is coped from https://github.com/bazelbuild/bazel/commit/d9a7d3a789be559bd6972208af21adae871d7a44
     * Adds line breaks to enforce a maximum 72 bytes per line.
     */
    private String make72Safe(String line) {
        StringBuilder result = new StringBuilder();
        int length = line.length();
        for (int i = 0; i < length; i += 69) {
            result.append(line, i, Math.min(i + 69, length));
            result.append("\r\n ");
        }
        return result.toString();
    }
}
