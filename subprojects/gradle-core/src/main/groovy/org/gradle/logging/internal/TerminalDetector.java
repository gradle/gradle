/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging.internal;

import org.fusesource.jansi.WindowsAnsiOutputStream;
import org.gradle.api.specs.Spec;
import org.gradle.util.OperatingSystem;
import org.gradle.util.PosixUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;

public class TerminalDetector implements Spec<FileDescriptor> {
    public boolean isSatisfiedBy(FileDescriptor element) {
        if (OperatingSystem.current().isWindows()) {
            // Use Jansi's detection mechanism
            try {
                new WindowsAnsiOutputStream(new ByteArrayOutputStream());
            } catch (IOException ignore) {
                // Not attached to a console
                return false;
            }
        }

        // Use jna-posix to determine if we're connected to a terminal
        if (!PosixUtil.current().isatty(element)) {
            return false;
        }

        // Dumb terminal doesn't support control codes. Should really be using termcap database.
        String term = System.getenv("TERM");
        if (term != null && term.equals("dumb")) {
            return false;
        }

        // Assume a terminal
        return true;
    }
}
