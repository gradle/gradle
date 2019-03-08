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
import org.gradle.internal.nativeintegration.console.TestConsoleMetadata;
import org.gradle.internal.nativeintegration.services.NativeServices;

import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class ConsoleConfigureAction {
    public static void execute(OutputEventRenderer renderer, ConsoleOutput consoleOutput) {
        execute(renderer, consoleOutput, getConsoleMetaData(), renderer.getOriginalStdOut(), renderer.getOriginalStdErr());
    }

    public static void execute(OutputEventRenderer renderer, ConsoleOutput consoleOutput, ConsoleMetaData consoleMetadata, OutputStream stdout, OutputStream stderr) {
        if (consoleOutput == ConsoleOutput.Auto) {
            configureAutoConsole(renderer, consoleMetadata, stdout, stderr);
        } else if (consoleOutput == ConsoleOutput.Rich) {
            configureRichConsole(renderer, consoleMetadata, stdout, stderr, false);
        } else if (consoleOutput == ConsoleOutput.Verbose) {
            configureRichConsole(renderer, consoleMetadata, stdout, stderr, true);
        } else if (consoleOutput == ConsoleOutput.Plain) {
            configurePlainConsole(renderer, consoleMetadata, stdout, stderr);
        }
    }

    private static ConsoleMetaData getConsoleMetaData() {
        String testConsole = System.getProperty(TestConsoleMetadata.TEST_CONSOLE_PROPERTY);
        if (testConsole != null) {
            return TestConsoleMetadata.valueOf(testConsole);
        }
        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
        ConsoleMetaData metaData = consoleDetector.getConsole();
        if (metaData != null) {
            return metaData;
        }
        return FallbackConsoleMetaData.NOT_ATTACHED;
    }

    private static void configureAutoConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr) {
        if (consoleMetaData.isStdOut() || consoleMetaData.isStdErr()) {
            configureRichConsole(renderer, consoleMetaData, stdout, stderr, false);
        } else {
            configurePlainConsole(renderer, consoleMetaData, stdout, stderr);
        }
    }

    private static void configurePlainConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr) {
        // Redirect stderr to stdout if a console is attached to both stdout and stderr
        // This avoids interleaving problems when stdout and stderr end up at the same location
        renderer.addPlainConsole(stdout, stderr, consoleMetaData != null && consoleMetaData.isStdOut() && consoleMetaData.isStdErr());
    }

    private static void configureRichConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr, boolean verbose) {
        boolean force = !consoleMetaData.isWrapStreams();
        if (consoleMetaData.isStdErr() && !consoleMetaData.isStdOut()) {
            // Only stderr is connected to a console. Currently we can write rich text to one stream only, so prefer stderr in this case
            OutputStreamWriter errStr = new OutputStreamWriter(force ? stderr : AnsiConsoleUtil.wrapOutputStream(stderr));
            Console console = new AnsiConsole(errStr, errStr, renderer.getColourMap(), consoleMetaData, force);
            renderer.addRichConsole(console, consoleMetaData, stdout, stderr, verbose);
        } else {
            // Prefer stdout when is connected to a console or neither stream connected to a console
            OutputStreamWriter outStr = new OutputStreamWriter(force ? stdout : AnsiConsoleUtil.wrapOutputStream(stdout));
            Console console = new AnsiConsole(outStr, outStr, renderer.getColourMap(), consoleMetaData, force);
            renderer.addRichConsole(console, consoleMetaData, stdout, stderr, verbose);
        }
    }
}
