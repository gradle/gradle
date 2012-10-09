/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeplatform;

import net.rubygrapefruit.platform.Terminals;

import java.io.FileDescriptor;

import static net.rubygrapefruit.platform.Terminals.Output.Stderr;
import static net.rubygrapefruit.platform.Terminals.Output.Stdout;

public class NativePlatformTerminalDetector implements TerminalDetector {
    private final Terminals terminals;

    public NativePlatformTerminalDetector(Terminals terminals) {
        this.terminals = terminals;
    }

    public boolean isTerminal(FileDescriptor fileDescriptor) {
        if (fileDescriptor == FileDescriptor.out) {
            return terminals.isTerminal(Stdout);
        } else if (fileDescriptor == FileDescriptor.err) {
            return terminals.isTerminal(Stderr);
        }
        return false;
    }
}
