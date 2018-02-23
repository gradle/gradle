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

package org.gradle.internal.nativeintegration.console;

import net.rubygrapefruit.platform.Terminals;
import org.gradle.internal.os.OperatingSystem;

import static net.rubygrapefruit.platform.Terminals.Output.Stderr;
import static net.rubygrapefruit.platform.Terminals.Output.Stdout;

public class NativePlatformConsoleDetector implements ConsoleDetector {
    private final Terminals terminals;

    public NativePlatformConsoleDetector(Terminals terminals) {
        this.terminals = terminals;
    }

    @Override
    public ConsoleMetaData getConsole() {
        // Dumb terminal doesn't support ANSI control codes.
        // TODO - remove this when we use Terminal rather than JAnsi to render to console
        String term = System.getenv("TERM");
        if ((term != null && term.equals("dumb")) || (OperatingSystem.current().isUnix() && term == null)) {
            return null;
        }

        boolean stdout = terminals.isTerminal(Stdout);
        boolean stderr = terminals.isTerminal(Stderr);
        if (stdout) {
            return new NativePlatformConsoleMetaData(stdout, stderr, terminals.getTerminal(Stdout));
        } else if (stderr) {
            return new NativePlatformConsoleMetaData(stdout, stderr, terminals.getTerminal(Stderr));
        }
        return null;
    }
}
