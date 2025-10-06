/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Minimal, best-effort text logger for TAPI help/version operations.
 * Writes to a deterministic txt file and swallows all I/O errors.
 */
public final class OperationTxtLogger {
    private OperationTxtLogger() {}

    public static void log(ConsumerOperationParameters params, String message) {
        try {
            File base = params.getGradleUserHomeDir();
            if (base == null) {
                base = params.getProjectDir();
            }
            if (base == null) {
                // Fall back to user home/.gradle if nothing else is available
                File userHome = new File(System.getProperty("user.home"), ".gradle");
                base = userHome;
            }
            Path logsDir = base.toPath().resolve("logs");
            Files.createDirectories(logsDir);
            Path logFile = logsDir.resolve("tapi-help-version.txt");
            String line = DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + " [consumer] " + message + System.lineSeparator();
            Files.write(logFile, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // Never fail the operation for logging problems
        }
    }
}
