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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.terminal.Terminals;
import org.fusesource.jansi.internal.Kernel32;
import org.gradle.internal.os.OperatingSystem;

import static net.rubygrapefruit.platform.terminal.Terminals.Output.Stderr;
import static net.rubygrapefruit.platform.terminal.Terminals.Output.Stdout;

public class NativePlatformConsoleDetector implements ConsoleDetector {
    private static final int WINDOWS_UTF8_CODEPAGE_ID = 65001;

    private final Terminals terminals;

    public NativePlatformConsoleDetector(Terminals terminals) {
        this.terminals = terminals;
    }

    @Override
    public ConsoleMetaData getConsole() {
        // Dumb terminal doesn't support ANSI control codes.
        // TODO - remove this when we use Terminal rather than JAnsi to render to console
        String term = System.getenv("TERM");
        OperatingSystem operatingSystem = OperatingSystem.current();
        if ("dumb".equals(term) || (operatingSystem.isUnix() && term == null)) {
            return null;
        }

        boolean isStdoutATerminal = terminals.isTerminal(Stdout);
        boolean isStderrATerminal = terminals.isTerminal(Stderr);
        boolean disableUnicodeSupportDetection = isWindowsWithNonUnicodeCodePage();

        try {
            if (isStdoutATerminal) {
                return new NativePlatformConsoleMetaData(isStdoutATerminal, isStderrATerminal, terminals.getTerminal(Stdout), disableUnicodeSupportDetection);
            } else if (isStderrATerminal) {
                return new NativePlatformConsoleMetaData(isStdoutATerminal, isStderrATerminal, terminals.getTerminal(Stderr), disableUnicodeSupportDetection);
            } else {
                return null;
            }
        } catch (NativeException ex) {
            // if a native terminal exists but cannot be resolved, use dumb terminal settings
            // this can happen if a terminal is in use that does not have its terminfo installed
            return null;
        }
    }

    static boolean isWindowsWithNonUnicodeCodePage() {
        //see https://learn.microsoft.com/en-us/windows/win32/intl/code-page-identifiers (e.g. code page 65001 is UTF-8)
        return OperatingSystem.current().isWindows() && Kernel32.GetConsoleOutputCP() != WINDOWS_UTF8_CODEPAGE_ID;
    }

    @Override
    public boolean isConsoleInput() {
        return terminals.isTerminalInput();
    }
}
