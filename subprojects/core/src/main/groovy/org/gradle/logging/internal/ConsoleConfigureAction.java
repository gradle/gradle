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
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.console.FallbackConsoleMetaData;
import org.gradle.internal.nativeintegration.services.NativeServices;

import java.io.OutputStream;
import java.io.PrintStream;

public class ConsoleConfigureAction implements Action<OutputEventRenderer> {
    private static boolean useAnsiOutput = "true".equalsIgnoreCase(System.getProperty("org.gradle.ansi", "false"));

    public void execute(OutputEventRenderer renderer) {
        ConsoleMetaData consoleMetaData = null;
        if(useAnsiOutput) {
            consoleMetaData = new FallbackConsoleMetaData();
        } else {
        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
            consoleMetaData = consoleDetector.getConsole();
        }
        if (consoleMetaData == null) {
            return;
        }
        boolean stdOutIsTerminal = consoleMetaData.isStdOut();
        boolean stdErrIsTerminal = consoleMetaData.isStdErr();
        if (stdOutIsTerminal) {
            OutputStream originalStdOut = renderer.getOriginalStdOut();
            PrintStream outStr = new PrintStream(useAnsiOutput ? originalStdOut : org.fusesource.jansi.AnsiConsole.wrapOutputStream(originalStdOut));
            Console console = new AnsiConsole(outStr, outStr, renderer.getColourMap(), useAnsiOutput);
            renderer.addConsole(console, true, stdErrIsTerminal, consoleMetaData);
        } else if (stdErrIsTerminal) {
            // Only stderr is connected to a terminal
            OutputStream originalStdErr = renderer.getOriginalStdErr();
            PrintStream errStr = new PrintStream(useAnsiOutput ? originalStdErr : org.fusesource.jansi.AnsiConsole.wrapOutputStream(originalStdErr));
            Console console = new AnsiConsole(errStr, errStr, renderer.getColourMap(), useAnsiOutput);
            renderer.addConsole(console, false, true, consoleMetaData);
        }
    }
}
