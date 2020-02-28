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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LockFileReaderWriter {

    private static final Logger LOGGER = Logging.getLogger(LockFileReaderWriter.class);
    private static final DocumentationRegistry DOC_REG = new DocumentationRegistry();

    static final String UNIQUE_LOCKFILE_NAME = "all_locks.singlelockfile";
    static final String FILE_SUFFIX = ".lockfile";
    static final String DEPENDENCY_LOCKING_FOLDER = "gradle/dependency-locks";
    static final Charset CHARSET = Charset.forName("UTF-8");
    static final String LOCKFILE_HEADER = "# This is a Gradle generated file for dependency locking.\n" +
                                                 "# Manual edits can break the build and are not advised.\n" +
                                                 "# This file is expected to be part of source control.\n";

    private final Path lockFilesRoot;
    private final DomainObjectContext context;

    public LockFileReaderWriter(FileResolver fileResolver, DomainObjectContext context) {
        this.context = context;
        Path resolve = null;
        if (fileResolver.canResolveRelativePath()) {
            resolve = fileResolver.resolve(DEPENDENCY_LOCKING_FOLDER).toPath();
        }
        this.lockFilesRoot = resolve;
        LOGGER.debug("Lockfiles root: {}", lockFilesRoot);
    }

    public void writeLockFile(String configurationName, List<String> resolvedModules) {
        checkValidRoot(configurationName);

        if (!Files.exists(lockFilesRoot)) {
            try {
                Files.createDirectories(lockFilesRoot);
            } catch (IOException e) {
                throw new RuntimeException("Issue creating dependency-lock directory", e);
            }
        }
        StringBuilder builder = new StringBuilder(LOCKFILE_HEADER);
        for (String module : resolvedModules) {
            builder.append(module).append("\n");
        }
        try {
            Files.write(lockFilesRoot.resolve(decorate(configurationName) + FILE_SUFFIX), builder.toString().getBytes(CHARSET));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write lock file", e);
        }
    }

    public List<String> readLockFile(String configurationName) {
        checkValidRoot(configurationName);

        Path lockFile = lockFilesRoot.resolve(decorate(configurationName) + FILE_SUFFIX);
        if (Files.exists(lockFile)) {
            List<String> lines = readAllLines(lockFile);
            filterNonModuleLines(lines);
            return lines;
        } else {
            return null;
        }
    }

    private String decorate(String configurationName) {
        if (context.isScript()) {
            return "buildscript-" + configurationName;
        } else {
            return configurationName;
        }
    }

    private void checkValidRoot(String configurationName) {
        if (lockFilesRoot == null) {
            throw new IllegalStateException("Dependency locking cannot be used for configuration '" + context.identityPath(configurationName) + "'." +
                " See limitations in the documentation (" + DOC_REG.getDocumentationFor("dependency_locking", "locking_limitations") +").");
        }
    }

    private void filterNonModuleLines(List<String> lines) {
        Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            String value = iterator.next().trim();
            if (value.startsWith("#") || value.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public Map<String, List<String>> readSingleLockFile() {
        if (lockFilesRoot == null) {
            throw new IllegalStateException("Dependency locking cannot be used for project '" + context.getProjectPath() + "'." +
                " See limitations in the documentation (" + DOC_REG.getDocumentationFor("dependency_locking", "locking_limitations") +").");
        }
        String fileName = decorate(UNIQUE_LOCKFILE_NAME);
        Path uniqueLockFile = lockFilesRoot.resolve(fileName);
        if (Files.exists(uniqueLockFile)) {
            List<String> lines = readAllLines(uniqueLockFile);
            filterNonModuleLines(lines);
            return processLines(lines);
        } else {
            return null;
        }
    }

    private Map<String, List<String>> processLines(List<String> lines) {
        HashMap<String, List<String>> result = new HashMap<>();
        for (int i = 0; i < lines.size() - 1; i++) {
            parseLine(lines.get(i), result);

        }
        parseEmptyConfigurations(lines.get(lines.size() - 1), result);
        return result;
    }

    private void parseEmptyConfigurations(String emptyConfigurations, HashMap<String, List<String>> result) {
        String[] split = emptyConfigurations.split("=");
        if (!split[0].equals("empty")) {
            throw new IllegalStateException("Lockfile does not end with an 'empty=...' entry");
        }
        if (split.length > 1) {
            String[] configurations = split[1].split(",");
            for (String configuration : configurations) {
                if (result.putIfAbsent(configuration, new ArrayList<>()) != null) {
                    throw new IllegalStateException("Lockfile lists configuration '" + configuration + "' as empty, but it had entries already");
                }
            }
        }
    }

    private void parseLine(String line, Map<String, List<String>> result) {
        String[] split = line.split("=");
        String[] configurations = split[1].split(",");
        for (String configuration : configurations) {
            result.compute(configuration, (k, v) -> {
                List<String> mapping;
                if (v == null) {
                    mapping = new ArrayList<>();
                } else {
                    mapping = v;
                }
                mapping.add(split[0]);
                return mapping;
            });
        }
    }

    private List<String> readAllLines(Path uniqueLockFile) {
        try {
            // TODO See if I can use a stream instead
            return Files.readAllLines(uniqueLockFile, CHARSET);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load lock file", e);
        }
    }
}
