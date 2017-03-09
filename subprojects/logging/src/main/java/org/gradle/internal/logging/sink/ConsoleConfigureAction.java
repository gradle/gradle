/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.logging.sink;

import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.internal.logging.console.AnsiConsole;
import org.gradle.internal.logging.console.Console;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.console.FallbackConsoleMetaData;
import org.gradle.internal.nativeintegration.services.NativeServices;

import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class ConsoleConfigureAction {
    public void execute(OutputEventRenderer renderer, ConsoleOutput consoleOutput) {
        if (consoleOutput == ConsoleOutput.Plain) {
            return;
        }

        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
        ConsoleMetaData consoleMetaData = consoleDetector.getConsole();
        boolean force = false;
        if (consoleMetaData == null) {
            if (consoleOutput == ConsoleOutput.Auto) {
                return;
            }
            assert consoleOutput == ConsoleOutput.Rich;
            consoleMetaData = new FallbackConsoleMetaData();
            force = true;
        }

        boolean stdOutIsTerminal = consoleMetaData.isStdOut();
        boolean stdErrIsTerminal = consoleMetaData.isStdErr();
        if (stdOutIsTerminal) {
            OutputStream originalStdOut = renderer.getOriginalStdOut();
            OutputStreamWriter outStr = new OutputStreamWriter(force ? originalStdOut : AnsiConsoleUtil.wrapOutputStream(originalStdOut));
            Console console = new AnsiConsole(outStr, outStr, renderer.getColourMap(), force);
            renderer.addConsole(console, true, stdErrIsTerminal, consoleMetaData);
        } else if (stdErrIsTerminal) {
            // Only stderr is connected to a terminal
            OutputStream originalStdErr = renderer.getOriginalStdErr();
            OutputStreamWriter errStr = new OutputStreamWriter(force ? originalStdErr : AnsiConsoleUtil.wrapOutputStream(originalStdErr));
            Console console = new AnsiConsole(errStr, errStr, renderer.getColourMap(), force);
            renderer.addConsole(console, false, true, consoleMetaData);
        }
    }
}
