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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.util.internal.GFileUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LockFileReaderWriter {

    private static final Logger LOGGER = Logging.getLogger(LockFileReaderWriter.class);
    private static final String LIMITATIONS_DOC_LINK = new DocumentationRegistry().getDocumentationRecommendationFor("information on limitations", "dependency_locking", "locking_limitations");
    static final String FORMATTING_DOC_LINK = "Verify the lockfile content. " + new DocumentationRegistry().getDocumentationRecommendationFor("information on lock file format", "dependency_locking", "lock_state_location_and_format");

    static final String UNIQUE_LOCKFILE_NAME = "gradle.lockfile";
    static final String FILE_SUFFIX = ".lockfile";
    static final String DEPENDENCY_LOCKING_FOLDER = "gradle/dependency-locks";
    static final Charset CHARSET = StandardCharsets.UTF_8;
    static final List<String> LOCKFILE_HEADER_LIST = ImmutableList.of("# This is a Gradle generated file for dependency locking.", "# Manual edits can break the build and are not advised.", "# This file is expected to be part of source control.");
    static final String EMPTY_RESOLUTIONS_ENTRY = "empty=";
    static final String BUILD_SCRIPT_PREFIX = "buildscript-";
    static final String SETTINGS_SCRIPT_PREFIX = "settings-";

    private final Path lockFilesRoot;
    private final DomainObjectContext context;
    private final RegularFileProperty lockFile;
    private final FileResourceListener listener;

    public LockFileReaderWriter(FileResolver fileResolver, DomainObjectContext context, RegularFileProperty lockFile, FileResourceListener listener) {
        this.context = context;
        this.lockFile = lockFile;
        this.listener = listener;
        Path resolve = null;
        if (fileResolver.canResolveRelativePath()) {
            resolve = fileResolver.resolve(DEPENDENCY_LOCKING_FOLDER).toPath();
            // TODO: Can I find a way to use a convention here instead?
            lockFile.set(fileResolver.resolve(decorate(UNIQUE_LOCKFILE_NAME)));
        }
        this.lockFilesRoot = resolve;
        LOGGER.debug("Lockfiles root: {}", lockFilesRoot);
    }

    @Nullable
    public List<String> readLockFile(String lockId) {
        checkValidRoot();

        Path lockFile = lockFilesRoot.resolve(decorate(lockId) + FILE_SUFFIX);
        listener.fileObserved(lockFile.toFile());
        if (Files.exists(lockFile)) {
            List<String> result;
            try {
                result = Files.readAllLines(lockFile, CHARSET);
            } catch (IOException e) {
                throw new RuntimeException("Unable to load lock file", e);
            }
            List<String> lines = result;
            filterNonModuleLines(lines);
            return lines;
        } else {
            return null;
        }
    }

    private String decorate(String lockId) {
        if (context.isScript()) {
            if (context.isRootScript()) {
                return SETTINGS_SCRIPT_PREFIX + lockId;
            }
            return BUILD_SCRIPT_PREFIX + lockId;
        } else {
            return lockId;
        }
    }

    private void checkValidRoot() {
        if (lockFilesRoot == null) {
            throw new IllegalStateException("Dependency locking cannot be used for " + context.getDisplayName() + ". " + LIMITATIONS_DOC_LINK);
        }
    }

    private static void filterNonModuleLines(List<String> lines) {
        Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            String value = iterator.next().trim();
            if (value.startsWith("#") || value.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public Map<String, List<String>> readUniqueLockFile() {
        checkValidRoot();
        Predicate<String> empty = String::isEmpty;
        Predicate<String> comment = s -> s.startsWith("#");
        Path uniqueLockFile = getUniqueLockfilePath();
        List<String> emptyLockIds = new ArrayList<>();
        Map<String, List<String>> uniqueLockState = new HashMap<>(10);
        listener.fileObserved(uniqueLockFile.toFile());
        if (Files.exists(uniqueLockFile)) {
            try (Stream<String> lines = Files.lines(uniqueLockFile, CHARSET)) {
                lines.filter(empty.or(comment).negate())
                    .filter(line -> {
                        if (line.startsWith(EMPTY_RESOLUTIONS_ENTRY)) {
                            // This is a perf hack for handling the last line in the file in a special way
                            collectEmptyLockIds(line, emptyLockIds);
                            return false;
                        } else {
                            return true;
                        }
                    }).forEach(line -> parseLine(line, uniqueLockState));
            } catch (IOException e) {
                throw new RuntimeException("Unable to load unique lockfile", e);
            }
            for (String emptyLockId : emptyLockIds) {
                uniqueLockState.computeIfAbsent(emptyLockId, k -> new ArrayList<>());
            }
            return uniqueLockState;
        } else {
            return new HashMap<>();
        }
    }

    private static void collectEmptyLockIds(String line, List<String> emptyLockIds) {
        if (line.length() > EMPTY_RESOLUTIONS_ENTRY.length()) {
            String[] lockIds = line.substring(EMPTY_RESOLUTIONS_ENTRY.length()).split(",");
            Collections.addAll(emptyLockIds, lockIds);
        }
    }

    private Path getUniqueLockfilePath() {
        return lockFile.get().getAsFile().toPath();
    }

    private void parseLine(String line, Map<String, List<String>> result) {
        String[] split = line.split("=");
        if (split.length != 2) {
            throw new InvalidLockFileException("lock file specified in '" + getUniqueLockfilePath().toString() + "'. Line: '" + line + "'", FORMATTING_DOC_LINK);
        }
        String[] lockIds = split[1].split(",");
        for (String lockId : lockIds) {
            result.compute(lockId, (k, v) -> {
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

    public boolean canWrite() {
        return lockFilesRoot != null;
    }

    public void writeUniqueLockfile(Map<String, List<String>> lockState) {
        checkValidRoot();
        Path lockfilePath = getUniqueLockfilePath();

        if (lockState.isEmpty()) {
            // Remove the file when no lock state
            GFileUtils.deleteQuietly(lockfilePath.toFile());
            return;
        }

        // Revert mapping
        Map<String, List<String>> dependencyToLockIds = new TreeMap<>();
        List<String> emptyLockIds = new ArrayList<>();
        mapLockStateFromDependencyToLockId(lockState, dependencyToLockIds, emptyLockIds);

        writeUniqueLockfile(lockfilePath, dependencyToLockIds, emptyLockIds);

        cleanupLegacyLockFiles(lockState.keySet());
    }

    /**
     * In prior versions of Gradle, each lock ID had its own lock file.
     * This method removes those lock files if they are present.
     */
    private void cleanupLegacyLockFiles(Set<String> lockIdsToDelete) {
        lockIdsToDelete.stream()
            .map(f -> lockFilesRoot.resolve(decorate(f) + FILE_SUFFIX))
            .map(Path::toFile)
            .forEach(GFileUtils::deleteQuietly);
    }

    private static void writeUniqueLockfile(Path lockfilePath, Map<String, List<String>> dependencyToLockId, List<String> emptyLockIds) {
        try {
            Files.createDirectories(lockfilePath.getParent());
            List<String> content = new ArrayList<>(50);
            content.addAll(LOCKFILE_HEADER_LIST);
            for (Map.Entry<String, List<String>> entry : dependencyToLockId.entrySet()) {
                String builder = entry.getKey() + "=" + entry.getValue().stream().sorted().collect(Collectors.joining(","));
                content.add(builder);
            }
            content.add("empty=" + emptyLockIds.stream().sorted().collect(Collectors.joining(",")));
            Files.write(lockfilePath, content, CHARSET);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write unique lockfile", e);
        }
    }

    private static void mapLockStateFromDependencyToLockId(Map<String, List<String>> lockState, Map<String, List<String>> dependencyToLockIds, List<String> emptyLockIds) {
        for (Map.Entry<String, List<String>> entry : lockState.entrySet()) {
            List<String> dependencies = entry.getValue();
            if (dependencies.isEmpty()) {
                emptyLockIds.add(entry.getKey());
            } else {
                for (String dependency : dependencies) {
                    dependencyToLockIds.compute(dependency, (k, v) -> {
                        List<String> confs = v;
                        if (v == null) {
                            confs = new ArrayList<>();
                        }
                        confs.add(entry.getKey());
                        return confs;
                    });
                }
            }
        }
    }
}
