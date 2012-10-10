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

package org.gradle.logging.internal;

import org.gradle.api.Action;
import org.gradle.internal.nativeplatform.ConsoleDetector;

import java.io.FileDescriptor;
import java.io.PrintStream;

public class ConsoleConfigureAction implements Action<OutputEventRenderer> {
    private final ConsoleDetector consoleDetector;

    public ConsoleConfigureAction(ConsoleDetector consoleDetector) {
        this.consoleDetector = consoleDetector;
    }

    public void execute(OutputEventRenderer renderer) {
        boolean stdOutIsTerminal = consoleDetector.isConsole(FileDescriptor.out);
        boolean stdErrIsTerminal = consoleDetector.isConsole(FileDescriptor.err);
        if (stdOutIsTerminal) {
            PrintStream outStr = org.fusesource.jansi.AnsiConsole.out();
            Console console = new AnsiConsole(outStr, outStr, renderer.getColourMap());
            renderer.addConsole(console, true, stdErrIsTerminal);
        } else if (stdErrIsTerminal) {
            // Only stderr is connected to a terminal
            PrintStream errStr = org.fusesource.jansi.AnsiConsole.err();
            Console console = new AnsiConsole(errStr, errStr, renderer.getColourMap());
            renderer.addConsole(console, false, true);
        }
    }
}
