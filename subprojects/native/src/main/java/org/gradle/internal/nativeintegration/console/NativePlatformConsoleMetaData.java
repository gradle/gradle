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

import net.rubygrapefruit.platform.Terminal;

public class NativePlatformConsoleMetaData implements ConsoleMetaData {
    private final boolean stdout;
    private final boolean stderr;
    private final Terminal terminal;

    public NativePlatformConsoleMetaData(boolean stdout, boolean stderr, Terminal terminal) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.terminal = terminal;
    }

    @Override
    public boolean isStdOut() {
        return stdout;
    }

    @Override
    public boolean isStdErr() {
        return stderr;
    }

    @Override
    public int getCols() {
        return terminal.getTerminalSize().getCols();
    }
}
