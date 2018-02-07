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
    public static void execute(OutputEventRenderer renderer, ConsoleOutput consoleOutput) {
        if (consoleOutput == ConsoleOutput.Auto) {
            configureAutoConsole(renderer);
        } else if (consoleOutput == ConsoleOutput.Rich) {
            configureRichConsole(renderer, false);
        } else if (consoleOutput == ConsoleOutput.Verbose) {
            configureRichConsole(renderer, true);
        }
    }

    private static void configureRichConsole(OutputEventRenderer renderer, boolean verbose) {
        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
        ConsoleMetaData consoleMetaData = consoleDetector.getConsole();
        configureConsole(renderer, consoleMetaData, consoleMetaData == null, verbose);
    }

    private static void configureAutoConsole(OutputEventRenderer renderer) {
        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
        ConsoleMetaData consoleMetaData = consoleDetector.getConsole();
        if (consoleMetaData != null) {
            configureConsole(renderer, consoleMetaData, false, false);
        }
    }

    private static void configureConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, boolean force, boolean verbose) {
        consoleMetaData = consoleMetaData == null ? FallbackConsoleMetaData.INSTANCE : consoleMetaData;
        if (consoleMetaData.isStdOut()) {
            OutputStream originalStdOut = renderer.getOriginalStdOut();
            OutputStreamWriter outStr = new OutputStreamWriter(force ? originalStdOut : AnsiConsoleUtil.wrapOutputStream(originalStdOut));
            Console console = new AnsiConsole(outStr, outStr, renderer.getColourMap(), consoleMetaData, force);
            renderer.addConsole(console, true, consoleMetaData.isStdErr(), consoleMetaData, verbose);
        } else if (consoleMetaData.isStdErr()) {
            // Only stderr is connected to a terminal
            OutputStream originalStdErr = renderer.getOriginalStdErr();
            OutputStreamWriter errStr = new OutputStreamWriter(force ? originalStdErr : AnsiConsoleUtil.wrapOutputStream(originalStdErr));
            Console console = new AnsiConsole(errStr, errStr, renderer.getColourMap(), consoleMetaData, force);
            renderer.addConsole(console, false, true, consoleMetaData, verbose);
        }
    }
}
