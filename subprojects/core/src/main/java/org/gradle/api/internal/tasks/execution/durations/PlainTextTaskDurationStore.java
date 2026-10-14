/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution.durations;

import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stores task duration history in a single human-readable text file, loaded entirely on first read.
 *
 * <pre>
 * # Gradle task duration history
 * # format: 1
 * :app:compileJava&lt;TAB&gt;1234,1500,1320
 * </pre>
 *
 * Each entry holds a build tree task path and up to {@link TaskDurations#MAX_SAMPLES} durations in milliseconds, oldest first.
 * A file that cannot be parsed is discarded.
 */
public class PlainTextTaskDurationStore implements TaskDurationStore {
    public static final String FILE_NAME = "task-durations.txt";

    private static final Logger LOGGER = LoggerFactory.getLogger(PlainTextTaskDurationStore.class);
    private static final String HEADER = "# Gradle task duration history";
    private static final String FORMAT = "# format: 1";
    private static final Object PROCESS_LOCK = new Object();

    private final File file;
    @Nullable
    private Map<Path, TaskDurations> entries;

    public PlainTextTaskDurationStore(File file) {
        this.file = file;
    }

    @Override
    @Nullable
    public synchronized TaskDurations read(Path buildTreeTaskPath) {
        return entries().get(buildTreeTaskPath);
    }

    @Override
    public synchronized void write(Map<Path, TaskDurations> updated) {
        // File locks are held per process, so also serialize stores for the same file within this process
        synchronized (PROCESS_LOCK) {
            File lockFile = new File(file.getPath() + ".lock");
            try {
                Files.createDirectories(lockFile.getAbsoluteFile().getParentFile().toPath());
                try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
                    mergeAndWrite(updated);
                }
            } catch (IOException e) {
                LOGGER.debug("Could not lock {}, writing task duration history without a lock.", lockFile, e);
                mergeAndWrite(updated);
            }
        }
    }

    private void mergeAndWrite(Map<Path, TaskDurations> updated) {
        // Merge with the current file content, as other builds may have written to it since it was loaded
        Map<Path, TaskDurations> merged = load();
        merged.putAll(updated);
        entries = merged;
        writeFile(merged);
    }

    private Map<Path, TaskDurations> entries() {
        if (entries == null) {
            entries = load();
        }
        return entries;
    }

    private Map<Path, TaskDurations> load() {
        if (!file.isFile()) {
            return new HashMap<>();
        }
        try {
            return parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Discarding task duration history in {}.", file, e);
            return new HashMap<>();
        }
    }

    private static Map<Path, TaskDurations> parse(List<String> lines) {
        if (lines.size() < 2 || !lines.get(0).equals(HEADER) || !lines.get(1).equals(FORMAT)) {
            throw new IllegalArgumentException("Unsupported task duration history format.");
        }
        Map<Path, TaskDurations> result = new HashMap<>();
        for (String line : lines.subList(2, lines.size())) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.lastIndexOf('\t');
            if (separator <= 0) {
                throw new IllegalArgumentException("Missing separator in: " + line);
            }
            Path path = Path.path(line.substring(0, separator));
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("Task path is not absolute: " + line);
            }
            List<Duration> durations = new ArrayList<>();
            for (String millis : line.substring(separator + 1).split(",", -1)) {
                long value = Long.parseLong(millis);
                if (value < 0) {
                    throw new IllegalArgumentException("Negative duration in: " + line);
                }
                durations.add(Duration.ofMillis(value));
            }
            result.put(path, TaskDurations.of(durations));
        }
        return result;
    }

    private void writeFile(Map<Path, TaskDurations> all) {
        StringBuilder content = new StringBuilder();
        content.append(HEADER).append('\n');
        content.append(FORMAT).append('\n');
        Map<Path, TaskDurations> sorted = new TreeMap<>(Comparator.comparing(Path::asString));
        sorted.putAll(all);
        for (Map.Entry<Path, TaskDurations> entry : sorted.entrySet()) {
            content.append(entry.getKey().asString()).append('\t');
            List<Duration> recent = entry.getValue().getRecent();
            for (int i = 0; i < recent.size(); i++) {
                if (i > 0) {
                    content.append(',');
                }
                content.append(recent.get(i).toMillis());
            }
            content.append('\n');
        }

        java.nio.file.Path tempFile = null;
        try {
            java.nio.file.Path dir = file.getAbsoluteFile().getParentFile().toPath();
            Files.createDirectories(dir);
            tempFile = Files.createTempFile(dir, FILE_NAME, ".tmp");
            Files.write(tempFile, content.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tempFile, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.debug("Could not write task duration history to {}.", file, e);
            if (tempFile != null) {
                tempFile.toFile().delete();
            }
        }
    }
}
