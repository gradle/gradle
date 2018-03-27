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

package org.gradle.internal.locking;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.file.FileResolver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LockFileReaderWriter {

    static final String FILE_SUFFIX = ".lockfile";
    static final String DEPENDENCY_LOCKING_FOLDER = "gradle/dependency-locks";
    static final Charset CHARSET = Charset.forName("UTF-8");
    static final String LOCKFILE_HEADER = "# This is a Gradle generated file for dependency locking.\n" +
                                                 "# Manual edits can break the build and are not advised.\n" +
                                                 "# This file is expected to be part of source control.\n";

    private final Path lockFilesRoot;

    public LockFileReaderWriter(FileResolver fileResolver) {
        Path resolve = null;
        try {
            resolve = fileResolver.resolve(DEPENDENCY_LOCKING_FOLDER).toPath();
        } catch (UnsupportedOperationException e) {
            // TODO Investigate if locking and no base dir can happen together
        }
        this.lockFilesRoot = resolve;
    }

    public Path resolve(String path) {
        return lockFilesRoot.resolve(path);
    }

    public void writeLockFile(String configurationName, Map<String, ModuleComponentIdentifier> resolvedModules) {
        if (!Files.exists(lockFilesRoot)) {
            try {
                Files.createDirectories(lockFilesRoot);
            } catch (IOException e) {
                throw new RuntimeException("Issue creating dependency-lock directory", e);
            }
        }
        StringBuilder builder = new StringBuilder(LOCKFILE_HEADER);
        for (Map.Entry<String, ModuleComponentIdentifier> entry : resolvedModules.entrySet()) {
            builder.append(entry.getKey()).append(':').append(entry.getValue().getVersion()).append("\n");
        }
        try {
            Files.write(lockFilesRoot.resolve(configurationName + FILE_SUFFIX), builder.toString().getBytes(CHARSET));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write lock file", e);
        }
    }

    public List<String> readLockFile(String configurationName) {
        try {
            Path lockFile = lockFilesRoot.resolve(configurationName + FILE_SUFFIX);
            if (Files.exists(lockFile)) {
                List<String> lines = Files.readAllLines(lockFile, CHARSET);
                filterNonModuleLines(lines);
                return lines;
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to load lock file");
        }

    }

    private void filterNonModuleLines(List<String> lines) {
        Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            String value = iterator.next();
            if (value.startsWith("#") || value.isEmpty()) {
                iterator.remove();
            }
        }
    }
}
