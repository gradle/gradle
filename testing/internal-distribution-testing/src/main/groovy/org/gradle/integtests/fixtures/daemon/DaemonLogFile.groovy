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

package org.gradle.integtests.fixtures.daemon


import org.gradle.util.GradleVersion

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.stream.Stream

/**
 * A wrapper class to read the daemon log file.
 */
class DaemonLogFile {
    private final File logFile
    private final Charset charset

    private DaemonLogFile(File logFile, Charset charset) {
        this.logFile = logFile
        this.charset = charset
    }

    File getFile() {
        return logFile
    }

    String getText() throws IOException {
        return logFile.getText(charset.name())
    }

    Stream<String> lines() throws IOException {
        return Files.lines(logFile.toPath(), charset)
    }

    @Override
    String toString() {
        return "DaemonLogFile(${logFile.absolutePath}, charset=${charset.name()})"
    }

    static DaemonLogFile forVersion(File logFile, GradleVersion version) {
        def logCharset = (version >= GradleVersion.version("8.9")) ? StandardCharsets.UTF_8 : Charset.defaultCharset()
        return new DaemonLogFile(logFile, logCharset)
    }
}
