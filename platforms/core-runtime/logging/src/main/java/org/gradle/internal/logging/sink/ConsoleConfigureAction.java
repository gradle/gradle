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
import org.gradle.internal.logging.console.ColorMap;
import org.gradle.internal.logging.console.Console;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.console.FallbackConsoleMetaData;
import org.gradle.internal.nativeintegration.services.NativeServices;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

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
        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
        ConsoleMetaData metaData = consoleDetector.getConsole();
        if (metaData != null) {
            return metaData;
        }
        return FallbackConsoleMetaData.NOT_ATTACHED;
    }

    private static void configureAutoConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr) {
        if (consoleMetaData.isStdOut() && consoleMetaData.isStdErr()) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console. Assume that they are attached to the same console
            // This avoids interleaving problems when stdout and stderr end up at the same location
            Console console = consoleFor(stdout, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsoleWithErrorOutputOnStdout(console, consoleMetaData, false);
        } else if (consoleMetaData.isStdOut()) {
            // Write rich content to stdout and plain content to stderr
            Console console = consoleFor(stdout, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsole(console, stderr, consoleMetaData, false);
        } else if (consoleMetaData.isStdErr()) {
            // Write plain content to stdout and rich content to stderr
            Console stderrConsole = consoleFor(stderr, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsole(stdout, stderrConsole, true);
        } else {
            renderer.addPlainConsole(stdout, stderr);
        }
    }

    private static void configurePlainConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr) {
        if (consoleMetaData.isStdOut() && consoleMetaData.isStdErr()) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console. Assume that they are attached to the same console
            // This avoids interleaving problems when stdout and stderr end up at the same location
            renderer.addPlainConsoleWithErrorOutputOnStdout(stdout);
        } else {
            renderer.addPlainConsole(stdout, stderr);
        }
    }

    private static void configureRichConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr, boolean verbose) {
        if (consoleMetaData.isStdOut() && consoleMetaData.isStdErr()) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console. Assume that they are attached to the same console
            // This avoids interleaving problems when stdout and stderr end up at the same location
            Console console = consoleFor(stdout, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsoleWithErrorOutputOnStdout(console, consoleMetaData, verbose);
        } else {
            // Write rich content to both stdout and stderr
            Console stdoutConsole = consoleFor(stdout, consoleMetaData, renderer.getColourMap());
            Console stderrConsole = consoleFor(stderr, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsole(stdoutConsole, stderrConsole, consoleMetaData, verbose);
        }
    }

    private static Console consoleFor(OutputStream stdout, ConsoleMetaData consoleMetaData, ColorMap colourMap) {
        boolean force = !consoleMetaData.isWrapStreams();
        OutputStreamWriter outStr = new OutputStreamWriter(force ? stdout : AnsiConsoleUtil.wrapOutputStream(stdout), Charset.defaultCharset());
        return new AnsiConsole(outStr, outStr, colourMap, consoleMetaData, force);
    }
}
