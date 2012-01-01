/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.specs.Spec;
import org.jruby.ext.posix.POSIX;

import java.io.FileDescriptor;

public class PosixBackedTerminalDetector implements Spec<FileDescriptor> {
    private final POSIX posix;

    public PosixBackedTerminalDetector(POSIX posix) {
        this.posix = posix;
    }

    public boolean isSatisfiedBy(FileDescriptor element) {
        // Determine if we're connected to a terminal
        if (!posix.isatty(element)) {
            return false;
        }

        // Dumb terminal doesn't support ANSI control codes. Should really be using termcap database.
        String term = System.getenv("TERM");
        if (term != null && term.equals("dumb")) {
            return false;
        }

        // Assume a terminal
        return true;
    }
}
