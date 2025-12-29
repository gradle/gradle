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

import org.fusesource.jansi.AnsiPrintStream;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.internal.logging.console.AnsiConsole;
import org.gradle.internal.logging.console.ColorMap;
import org.gradle.internal.logging.console.Console;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.console.FallbackConsoleMetaData;
import org.gradle.internal.nativeintegration.services.NativeServices;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.function.Supplier;

public class ConsoleConfigureAction {

    private ConsoleConfigureAction() {
    }

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
        } else if (consoleOutput == ConsoleOutput.Colored) {
            configureColoredConsole(renderer, consoleMetadata, stdout, stderr);
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
        if (consoleMetaData.isStdOutATerminal() && consoleMetaData.isStdErrATerminal()) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console. Assume that they are attached to the same console
            // This avoids interleaving problems when stdout and stderr end up at the same location
            Console console = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsoleWithErrorOutputOnStdout(console, consoleMetaData, false);
        } else if (consoleMetaData.isStdOutATerminal()) {
            // Write rich content to stdout and plain content to stderr
            Console stdoutConsole = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsole(stdoutConsole, stderr, consoleMetaData, false);
        } else if (consoleMetaData.isStdErrATerminal()) {
            // Write plain content to stdout and rich content to stderr
            Console stderrConsole = consoleForStdErr(stderr, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsole(stdout, stderrConsole, true);
        } else {
            renderer.addPlainConsole(stdout, stderr);
        }
    }

    private static void configurePlainConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr) {
        if (consoleMetaData.isStdOutATerminal() && consoleMetaData.isStdErrATerminal()) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console. Assume that they are attached to the same console
            // This avoids interleaving problems when stdout and stderr end up at the same location
            renderer.addPlainConsoleWithErrorOutputOnStdout(stdout);
        } else {
            renderer.addPlainConsole(stdout, stderr);
        }
    }

    private static void configureColoredConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr) {
        if (consoleMetaData.isStdOutATerminal() && consoleMetaData.isStdErrATerminal()) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console.
            // Assume that they are attached to the same console.
            // This avoids interleaving problems when stdout and stderr end up at the same location.
            Console console = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap());
            renderer.addColoredConsoleWithErrorOutputOnStdout(console);
        } else {
            // Write colored content to both stdout and stderr
            Console stdoutConsole = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap());
            Console stderrConsole = consoleForStdErr(stderr, consoleMetaData, renderer.getColourMap());
            renderer.addColoredConsole(stdoutConsole, stderrConsole);
        }
    }

    private static void configureRichConsole(OutputEventRenderer renderer, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr, boolean verbose) {
        if (consoleMetaData.isStdOutATerminal() && consoleMetaData.isStdErrATerminal()) {
            // Redirect stderr to stdout when both stdout and stderr are attached to a console.
            // Assume that they are attached to the same console.
            // This avoids interleaving problems when stdout and stderr end up at the same location.
            Console console = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsoleWithErrorOutputOnStdout(console, consoleMetaData, verbose);
        } else {
            // Write rich content to both stdout and stderr
            Console stdoutConsole = consoleForStdOut(stdout, consoleMetaData, renderer.getColourMap());
            Console stderrConsole = consoleForStdErr(stderr, consoleMetaData, renderer.getColourMap());
            renderer.addRichConsole(stdoutConsole, stderrConsole, consoleMetaData, verbose);
        }
    }

    private static Console consoleFor(OutputStream stream, Supplier<OutputStream> jansiFallback, ConsoleMetaData consoleMetaData, ColorMap colourMap) {
        boolean force = !consoleMetaData.isWrapStreams();
        OutputStreamWriter writer = new OutputStreamWriter(force ? stream : jansiFallback.get(), Charset.defaultCharset());
        return new AnsiConsole(writer, writer, colourMap, consoleMetaData, force);
    }

    private static Console consoleForStdOut(OutputStream stdout, ConsoleMetaData consoleMetaData, ColorMap colourMap) {
        return consoleFor(stdout, () -> installJansiStream(org.fusesource.jansi.AnsiConsole.out()), consoleMetaData, colourMap);
    }

    private static Console consoleForStdErr(OutputStream stderr, ConsoleMetaData consoleMetaData, ColorMap colourMap) {
        return consoleFor(stderr, () -> installJansiStream(org.fusesource.jansi.AnsiConsole.err()), consoleMetaData, colourMap);
    }

    /**
     * If any changes are made to the use of JANSI here, try out gradle on a windows CMD.EXE terminal.
     *
     * @return the installed ansiPrintStream
     */
    private static OutputStream installJansiStream(AnsiPrintStream ansiPrintStream) {
        try {
            ansiPrintStream.install();
        } catch (IOException e) {
            // compiler appeasement, no exception should be thrown according to the jansi code
            throw new UncheckedIOException(e);
        }
        return ansiPrintStream;
    }
}
