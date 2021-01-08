/*
 * Copyright 2017 the original author or authors.
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

import org.apache.commons.io.output.ProxyOutputStream;
import org.fusesource.jansi.AnsiOutputStream;
import org.fusesource.jansi.WindowsAnsiPrintStream;
import org.gradle.internal.os.OperatingSystem;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.fusesource.jansi.internal.CLibrary.STDOUT_FILENO;
import static org.fusesource.jansi.internal.CLibrary.isatty;
import static org.fusesource.jansi.internal.Kernel32.GetConsoleMode;
import static org.fusesource.jansi.internal.Kernel32.GetStdHandle;
import static org.fusesource.jansi.internal.Kernel32.INVALID_HANDLE_VALUE;
import static org.fusesource.jansi.internal.Kernel32.STD_OUTPUT_HANDLE;
import static org.fusesource.jansi.internal.Kernel32.SetConsoleMode;

/**
 * @see <a href="https://github.com/gradle/gradle/issues/882">Original issue (gradle/gradle#882)</a>
 * @see <a href="https://github.com/fusesource/jansi/issues/69">Issue in 3rd party library (fusesource/jansi#69)</a>
 */
final class AnsiConsoleUtil {
    private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
    private static final int DISABLE_NEWLINE_AUTO_RETURN = 0x0008;

    private static final boolean IS_XTERM = environmentVariableStartsWith("TERM", "xterm");
    private static final boolean IS_MINGW_XTERM = IS_XTERM && environmentVariableStartsWith("MSYSTEM", "MINGW");

    private static boolean environmentVariableStartsWith(String name, String pattern) {
        return System.getenv(name) != null && System.getenv(name).startsWith(pattern);
    }

    private AnsiConsoleUtil() {
    }

    /**
     * @see <a href="https://github.com/fusesource/jansi/blob/eeda18cb05122abe48b284dca969e2c060a0c009/jansi/src/main/java/org/fusesource/jansi/AnsiConsole.java#L48-L54">Method copied over from AnsiConsole.wrapOutputStream</a>
     */
    static OutputStream wrapOutputStream(final OutputStream stream) {
        try {
            return wrapOutputStream(stream, STDOUT_FILENO);
        } catch (Throwable ignore) {
            return wrapOutputStream(stream, 0);
        }
    }

    /**
     * @see <a href="https://github.com/fusesource/jansi/blob/eeda18cb05122abe48b284dca969e2c060a0c009/jansi/src/main/java/org/fusesource/jansi/AnsiConsole.java#L64-L119">Method copied over from AnsiConsole.wrapOutputStream</a>
     */
    private static OutputStream wrapOutputStream(final OutputStream stream, int fileno) {

        // If the jansi.passthrough property is set, then don't interpret
        // any of the ansi sequences.
        if (Boolean.getBoolean("jansi.passthrough")) {
            return stream;
        }

        // If the jansi.strip property is set, then we just strip the
        // the ansi escapes.
        if (Boolean.getBoolean("jansi.strip")) {
            return new AnsiOutputStream(stream);
        }

        if (OperatingSystem.current().isWindows() && !IS_MINGW_XTERM) {
            final long stdOutputHandle = GetStdHandle(STD_OUTPUT_HANDLE);
            final int[] mode = new int[1];
            if (stdOutputHandle != INVALID_HANDLE_VALUE
                && 0 != GetConsoleMode(stdOutputHandle, mode)
                && 0 != SetConsoleMode(stdOutputHandle, mode[0] | ENABLE_VIRTUAL_TERMINAL_PROCESSING | DISABLE_NEWLINE_AUTO_RETURN)) {
                return new ProxyOutputStream(stream) {
                    @Override
                    public void close() throws IOException {
                        write(AnsiOutputStream.RESET_CODE);
                        flush();

                        // Reset console mode
                        SetConsoleMode(stdOutputHandle, mode[0]);

                        super.close();
                    }
                };
            }

            // On windows we know the console does not interpret ANSI codes..
            try {
                return new WindowsAnsiPrintStream(new PrintStream(stream));
            } catch (Throwable ignore) {
                // this happens when JNA is not in the path.. or
                // this happens when the stdout is being redirected to a file.
            }

            // Use the ANSIOutputStream to strip out the ANSI escape sequences.
            return new AnsiOutputStream(stream);
        }

        // We must be on some Unix variant, including MSYS(2) on Windows...
        try {
            // If the jansi.force property is set, then we force to output
            // the ansi escapes for piping it into ansi color aware commands (e.g. less -r)
            boolean forceColored = Boolean.getBoolean("jansi.force");
            // If we can detect that stdout is not a tty.. then setup
            // to strip the ANSI sequences..
            if (!IS_XTERM && !forceColored && isatty(fileno) == 0) {
                return new AnsiOutputStream(stream);
            }
        } catch (Throwable ignore) {
            // These errors happen if the JNI lib is not available for your platform.
            // But since we are on ANSI friendly platform, assume the user is on the console.
        }

        // By default we assume your Unix tty can handle ANSI codes.
        // Just wrap it up so that when we get closed, we reset the
        // attributes.
        return new ProxyOutputStream(stream) {
            @Override
            public void close() throws IOException {
                write(AnsiOutputStream.RESET_CODE);
                flush();
                super.close();
            }
        };
    }

}

