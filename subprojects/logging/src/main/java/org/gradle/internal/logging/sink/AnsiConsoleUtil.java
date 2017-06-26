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

import org.fusesource.jansi.AnsiOutputStream;
import org.fusesource.jansi.internal.Kernel32.*;
import org.fusesource.jansi.internal.WindowsSupport;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.fusesource.jansi.internal.CLibrary.STDOUT_FILENO;
import static org.fusesource.jansi.internal.CLibrary.isatty;
import static org.fusesource.jansi.internal.Kernel32.*;

/**
 * @see <a href="https://github.com/gradle/gradle/issues/882">Original issue (gradle/gradle#882)</a>
 * @see <a href="https://github.com/fusesource/jansi/issues/69">Issue in 3rd party library (fusesource/jansi#69)</a>
 */
public final class AnsiConsoleUtil {
    private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
    private static final int DISABLE_NEWLINE_AUTO_RETURN = 0x0008;

    private AnsiConsoleUtil() {}

    /**
     * @see <a href="https://github.com/fusesource/jansi/blob/eeda18cb05122abe48b284dca969e2c060a0c009/jansi/src/main/java/org/fusesource/jansi/AnsiConsole.java#L48-L54">Method copied over from AnsiConsole.wrapOutputStream</a>
     */
    public static OutputStream wrapOutputStream(final OutputStream stream) {
        try {
            return wrapOutputStream(stream, STDOUT_FILENO);
        } catch (Throwable ignore) {
            return wrapOutputStream(stream, 0);
        }
    }

    /**
     * @see <a href="https://github.com/fusesource/jansi/blob/eeda18cb05122abe48b284dca969e2c060a0c009/jansi/src/main/java/org/fusesource/jansi/AnsiConsole.java#L64-L119">Method copied over from AnsiConsole.wrapOutputStream</a>
     */
    public static OutputStream wrapOutputStream(final OutputStream stream, int fileno) {

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

        String os = System.getProperty("os.name");
        if (os.startsWith("Windows") && !isXterm()) {
            final long stdOutputHandle = GetStdHandle(STD_OUTPUT_HANDLE);
            final int[] mode = new int[1];
            if (stdOutputHandle != INVALID_HANDLE_VALUE
                && 0 != GetConsoleMode(stdOutputHandle, mode)
                && 0 != SetConsoleMode(stdOutputHandle, mode[0] | ENABLE_VIRTUAL_TERMINAL_PROCESSING | DISABLE_NEWLINE_AUTO_RETURN)) {
                return new FilterOutputStream(stream) {
                    @Override
                    public void close() throws IOException {
                        write(AnsiOutputStream.REST_CODE);
                        flush();

                        // Reset console mode
                        SetConsoleMode(stdOutputHandle, mode[0]);

                        super.close();
                    }
                };
            }

            // On windows we know the console does not interpret ANSI codes..
            try {
                return new WindowsAnsiOutputStream(stream);
            } catch (Throwable ignore) {
                // this happens when JNA is not in the path.. or
                // this happens when the stdout is being redirected to a file.
            }

            // Use the ANSIOutputStream to strip out the ANSI escape sequences.
            return new AnsiOutputStream(stream);
        }

        // We must be on some Unix variant, including Cygwin or MSYS(2) on Windows...
        try {
            // If the jansi.force property is set, then we force to output
            // the ansi escapes for piping it into ansi color aware commands (e.g. less -r)
            boolean forceColored = Boolean.getBoolean("jansi.force");
            // If we can detect that stdout is not a tty.. then setup
            // to strip the ANSI sequences..
            if (!isXterm() && !forceColored && isatty(fileno) == 0) {
                return new AnsiOutputStream(stream);
            }
        } catch (Throwable ignore) {
            // These errors happen if the JNI lib is not available for your platform.
            // But since we are on ANSI friendly platform, assume the user is on the console.
        }

        // By default we assume your Unix tty can handle ANSI codes.
        // Just wrap it up so that when we get closed, we reset the
        // attributes.
        return new FilterOutputStream(stream) {
            @Override
            public void close() throws IOException {
                write(AnsiOutputStream.REST_CODE);
                flush();
                super.close();
            }
        };
    }

    /**
     * @see <a href="https://github.com/fusesource/jansi/blob/eeda18cb05122abe48b284dca969e2c060a0c009/jansi/src/main/java/org/fusesource/jansi/AnsiConsole.java#L121-L124">Method copied over from AnsiConsole.isXterm</a>
     */
    private static boolean isXterm() {
        String term = System.getenv("TERM");
        return term != null && term.startsWith("xterm");
    }

    /**
     * @see <a href="https://github.com/fusesource/jansi/blob/eeda18cb05122abe48b284dca969e2c060a0c009/jansi/src/main/java/org/fusesource/jansi/WindowsAnsiOutputStream.java">Class copied over and patched from fusesource/jansi</a>
     */
    private static class WindowsAnsiOutputStream extends AnsiOutputStream {

        private static final long CONSOLE = GetStdHandle(STD_OUTPUT_HANDLE);

        private static final short FOREGROUND_BLACK = 0;
        private static final short FOREGROUND_YELLOW = (short) (FOREGROUND_RED | FOREGROUND_GREEN);
        private static final short FOREGROUND_MAGENTA = (short) (FOREGROUND_BLUE | FOREGROUND_RED);
        private static final short FOREGROUND_CYAN = (short) (FOREGROUND_BLUE | FOREGROUND_GREEN);
        private static final short FOREGROUND_WHITE = (short) (FOREGROUND_RED | FOREGROUND_GREEN | FOREGROUND_BLUE);

        private static final short FOREGROUND_INTENSITY = 0x0008;

        private static final short BACKGROUND_BLACK = 0;
        private static final short BACKGROUND_YELLOW = (short) (BACKGROUND_RED | BACKGROUND_GREEN);
        private static final short BACKGROUND_MAGENTA = (short) (BACKGROUND_BLUE | BACKGROUND_RED);
        private static final short BACKGROUND_CYAN = (short) (BACKGROUND_BLUE | BACKGROUND_GREEN);
        private static final short BACKGROUND_WHITE = (short) (BACKGROUND_RED | BACKGROUND_GREEN | BACKGROUND_BLUE);

        private static final short[] ANSI_FOREGROUND_COLOR_MAP = {
            FOREGROUND_BLACK,
            FOREGROUND_RED,
            FOREGROUND_GREEN,
            FOREGROUND_YELLOW,
            FOREGROUND_BLUE,
            FOREGROUND_MAGENTA,
            FOREGROUND_CYAN,
            FOREGROUND_WHITE,
        };

        private static final short[] ANSI_BACKGROUND_COLOR_MAP = {
            BACKGROUND_BLACK,
            BACKGROUND_RED,
            BACKGROUND_GREEN,
            BACKGROUND_YELLOW,
            BACKGROUND_BLUE,
            BACKGROUND_MAGENTA,
            BACKGROUND_CYAN,
            BACKGROUND_WHITE,
        };

        private final CONSOLE_SCREEN_BUFFER_INFO info = new CONSOLE_SCREEN_BUFFER_INFO();
        private final short originalColors;

        private boolean negative;
        private short savedX = -1;
        private short savedY = -1;

        public WindowsAnsiOutputStream(OutputStream os) throws IOException {
            super(os);
            getConsoleInfo();
            originalColors = info.attributes;
        }

        private void getConsoleInfo() throws IOException {
            out.flush();
            if (GetConsoleScreenBufferInfo(CONSOLE, info) == 0) {
                throw new IOException("Could not get the screen info: " + WindowsSupport.getLastErrorMessage());
            }
            if (negative) {
                info.attributes = invertAttributeColors(info.attributes);
            }
        }

        private void applyAttribute() throws IOException {
            out.flush();
            short attributes = info.attributes;
            if (negative) {
                attributes = invertAttributeColors(attributes);
            }
            if (SetConsoleTextAttribute(CONSOLE, attributes) == 0) {
                throw new IOException(WindowsSupport.getLastErrorMessage());
            }
        }

        private short invertAttributeColors(short attributes) {
            // Swap the Foreground and Background bits.
            int fg = 0x000F & attributes;
            fg <<= 8;
            int bg = 0X00F0 * attributes;
            bg >>= 8;
            attributes = (short) ((attributes & 0xFF00) | fg | bg);
            return attributes;
        }

        private void applyCursorPosition() throws IOException {
            if (SetConsoleCursorPosition(CONSOLE, info.cursorPosition.copy()) == 0) {
                throw new IOException(WindowsSupport.getLastErrorMessage());
            }
        }

        @Override
        protected void processEraseScreen(int eraseOption) throws IOException {
            getConsoleInfo();
            int[] written = new int[1];
            switch (eraseOption) {
                case ERASE_SCREEN:
                    COORD topLeft = new COORD();
                    topLeft.x = 0;
                    topLeft.y = info.window.top;
                    int screenLength = info.window.height() * info.size.x;
                    FillConsoleOutputAttribute(CONSOLE, originalColors, screenLength, topLeft, written);
                    FillConsoleOutputCharacterW(CONSOLE, ' ', screenLength, topLeft, written);
                    break;
                case ERASE_SCREEN_TO_BEGINING:
                    COORD topLeft2 = new COORD();
                    topLeft2.x = 0;
                    topLeft2.y = info.window.top;
                    int lengthToCursor = (info.cursorPosition.y - info.window.top) * info.size.x
                        + info.cursorPosition.x;
                    FillConsoleOutputAttribute(CONSOLE, originalColors, lengthToCursor, topLeft2, written);
                    FillConsoleOutputCharacterW(CONSOLE, ' ', lengthToCursor, topLeft2, written);
                    break;
                case ERASE_SCREEN_TO_END:
                    int lengthToEnd = (info.window.bottom - info.cursorPosition.y) * info.size.x
                        + (info.size.x - info.cursorPosition.x);
                    FillConsoleOutputAttribute(CONSOLE, originalColors, lengthToEnd, info.cursorPosition.copy(), written);
                    FillConsoleOutputCharacterW(CONSOLE, ' ', lengthToEnd, info.cursorPosition.copy(), written);
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void processEraseLine(int eraseOption) throws IOException {
            getConsoleInfo();
            int[] written = new int[1];
            switch (eraseOption) {
                case ERASE_LINE:
                    COORD leftColCurrRow = info.cursorPosition.copy();
                    leftColCurrRow.x = 0;
                    FillConsoleOutputAttribute(CONSOLE, originalColors, info.size.x, leftColCurrRow, written);
                    FillConsoleOutputCharacterW(CONSOLE, ' ', info.size.x, leftColCurrRow, written);
                    break;
                case ERASE_LINE_TO_BEGINING:
                    COORD leftColCurrRow2 = info.cursorPosition.copy();
                    leftColCurrRow2.x = 0;
                    FillConsoleOutputAttribute(CONSOLE, originalColors, info.cursorPosition.x, leftColCurrRow2, written);
                    FillConsoleOutputCharacterW(CONSOLE, ' ', info.cursorPosition.x, leftColCurrRow2, written);
                    break;
                case ERASE_LINE_TO_END:
                    int lengthToLastCol = info.size.x - info.cursorPosition.x;
                    FillConsoleOutputAttribute(CONSOLE, originalColors, lengthToLastCol, info.cursorPosition.copy(), written);
                    FillConsoleOutputCharacterW(CONSOLE, ' ', lengthToLastCol, info.cursorPosition.copy(), written);
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void processCursorLeft(int count) throws IOException {
            getConsoleInfo();
            info.cursorPosition.x = (short) Math.max(0, info.cursorPosition.x - count);
            applyCursorPosition();
        }

        @Override
        protected void processCursorRight(int count) throws IOException {
            getConsoleInfo();
            info.cursorPosition.x = (short) Math.min(info.window.width(), info.cursorPosition.x + count);
            applyCursorPosition();
        }

        @Override
        protected void processCursorDown(int count) throws IOException {
            getConsoleInfo();
            // The following code was patched according to the following PR: https://github.com/fusesource/jansi/pull/70
            info.cursorPosition.y = (short) Math.min(Math.max(0, info.size.y - 1), info.cursorPosition.y + count);
            applyCursorPosition();
        }

        @Override
        protected void processCursorUp(int count) throws IOException {
            getConsoleInfo();
            info.cursorPosition.y = (short) Math.max(info.window.top, info.cursorPosition.y - count);
            applyCursorPosition();
        }

        @Override
        protected void processCursorTo(int row, int col) throws IOException {
            getConsoleInfo();
            info.cursorPosition.y = (short) Math.max(info.window.top, Math.min(info.size.y, info.window.top + row - 1));
            info.cursorPosition.x = (short) Math.max(0, Math.min(info.window.width(), col - 1));
            applyCursorPosition();
        }

        @Override
        protected void processCursorToColumn(int x) throws IOException {
            getConsoleInfo();
            info.cursorPosition.x = (short) Math.max(0, Math.min(info.window.width(), x - 1));
            applyCursorPosition();
        }

        @Override
        protected void processSetForegroundColor(int color, boolean bright) throws IOException {
            info.attributes = (short) ((info.attributes & ~0x000F) | ANSI_FOREGROUND_COLOR_MAP[color]);
            if (bright) {
                info.attributes |= FOREGROUND_INTENSITY;
            }
            applyAttribute();
        }

        @Override
        protected void processSetBackgroundColor(int color, boolean bright) throws IOException {
            info.attributes = (short) ((info.attributes & ~0x0070) | ANSI_BACKGROUND_COLOR_MAP[color]);
            applyAttribute();
        }

        @Override
        protected void processDefaultTextColor() throws IOException {
            info.attributes = (short) ((info.attributes & ~0x000F) | (originalColors & 0xF));
            applyAttribute();
        }

        @Override
        protected void processDefaultBackgroundColor() throws IOException {
            info.attributes = (short) ((info.attributes & ~0x00F0) | (originalColors & 0xF0));
            applyAttribute();
        }

        @Override
        protected void processAttributeRest() throws IOException {
            info.attributes = (short) ((info.attributes & ~0x00FF) | originalColors);
            this.negative = false;
            applyAttribute();
        }

        @Override
        protected void processSetAttribute(int attribute) throws IOException {
            switch (attribute) {
                case ATTRIBUTE_INTENSITY_BOLD:
                    info.attributes = (short) (info.attributes | FOREGROUND_INTENSITY);
                    applyAttribute();
                    break;
                case ATTRIBUTE_INTENSITY_NORMAL:
                    info.attributes = (short) (info.attributes & ~FOREGROUND_INTENSITY);
                    applyAttribute();
                    break;

                // Yeah, setting the background intensity is not underlining.. but it's best we can do
                // using the Windows console API
                case ATTRIBUTE_UNDERLINE:
                    info.attributes = (short) (info.attributes | BACKGROUND_INTENSITY);
                    applyAttribute();
                    break;
                case ATTRIBUTE_UNDERLINE_OFF:
                    info.attributes = (short) (info.attributes & ~BACKGROUND_INTENSITY);
                    applyAttribute();
                    break;

                case ATTRIBUTE_NEGATIVE_ON:
                    negative = true;
                    applyAttribute();
                    break;
                case ATTRIBUTE_NEGATIVE_Off:
                    negative = false;
                    applyAttribute();
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void processSaveCursorPosition() throws IOException {
            getConsoleInfo();
            savedX = info.cursorPosition.x;
            savedY = info.cursorPosition.y;
        }

        @Override
        protected void processRestoreCursorPosition() throws IOException {
            // restore only if there was a save operation first
            if (savedX != -1 && savedY != -1) {
                out.flush();
                info.cursorPosition.x = savedX;
                info.cursorPosition.y = savedY;
                applyCursorPosition();
            }
        }

        @Override
        protected void processChangeWindowTitle(String label) {
            SetConsoleTitle(label);
        }
    }
}
