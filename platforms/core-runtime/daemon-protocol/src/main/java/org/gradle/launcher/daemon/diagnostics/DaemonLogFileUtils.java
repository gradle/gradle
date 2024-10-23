/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.diagnostics;

import com.google.common.collect.EvictingQueue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracted utility methods to parse daemon logs.
 */
final class DaemonLogFileUtils {
    private DaemonLogFileUtils() {}

    /**
     * Reads the last {@code tailSize} lines of the daemon log file and returns them as a single string.
     *
     * @param file the daemon log
     * @param tailSize the length of tail
     * @return the tail of the log or a special {@code "<<empty>>"} string if the log is empty
     */
    static String tail(File file, int tailSize) throws IOException {
        try (Stream<String> lines = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
            EvictingQueue<String> tailLines = lines.collect(Collectors.toCollection(() -> EvictingQueue.create(tailSize)));
            return tailLines.isEmpty() ? "<<empty>>" : String.join("\n", tailLines);
        }
    }
}
