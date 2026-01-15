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

import java.util.Locale;

public interface ConsoleMetaData {
    /**
     * Returns true if the current process' stdout is attached to the console.
     */
    boolean isStdOutATerminal();

    /**
     * Returns true if the current process' stderr is attached to the console.
     */
    boolean isStdErrATerminal();

    /**
     * <p>Returns the number of columns available in the console.</p>
     *
     * @return The number of columns available in the console. If no information is available return 0.
     */
    int getCols();

    /**
     * <p>Returns the number of rows available in the console.</p>
     *
     * @return The height of the console (rows). If no information is available return 0.
     */
    int getRows();

    boolean isWrapStreams();

    /**
     * <p>Returns true if the console supports Unicode characters (e.g., box-drawing, block elements).</p>
     *
     * <p>This is determined by checking terminal capabilities such as UTF-8 encoding support,
     * terminal type, and platform-specific indicators.</p>
     *
     * @return true if Unicode characters can be safely displayed, false otherwise
     */
    default boolean supportsUnicode() {
        // Auto-detection logic
        // Check for UTF-8 encoding in locale, including common "UTF8" alias
        String lang = System.getenv("LANG");
        String lcAll = System.getenv("LC_ALL");
        if ((lang != null && (lang.toUpperCase(Locale.ROOT).contains("UTF-8") || lang.toUpperCase(Locale.ROOT).contains("UTF8"))) ||
            (lcAll != null && (lcAll.toUpperCase(Locale.ROOT).contains("UTF-8") || lcAll.toUpperCase(Locale.ROOT).contains("UTF8")))) {
            return true;
        }

        // Check for modern terminal types that support Unicode
        String term = System.getenv("TERM");
        if (term != null) {
            String lowerTerm = term.toLowerCase(Locale.ROOT);
            // Modern terminals that support Unicode well
            if (lowerTerm.contains("xterm") ||
                lowerTerm.contains("256color") ||
                lowerTerm.contains("screen") ||
                lowerTerm.contains("tmux") ||
                lowerTerm.contains("rxvt") ||
                lowerTerm.contains("konsole") ||
                lowerTerm.contains("gnome") ||
                lowerTerm.contains("alacritty") ||
                lowerTerm.contains("kitty") ||
                lowerTerm.contains("ghostty") ||
                lowerTerm.contains("wezterm") ||
                lowerTerm.contains("contour") ||
                lowerTerm.contains("foot") ||
                lowerTerm.contains("mlterm") ||
                lowerTerm.equals("st") ||
                lowerTerm.startsWith("st-") ||
                lowerTerm.contains("qterminal") ||
                lowerTerm.contains("weston")) {
                return true;
            }
            // Explicitly dumb terminals don't support Unicode
            if (lowerTerm.equals("dumb") || lowerTerm.equals("unknown")) {
                return false;
            }
        }

        // Windows Terminal and other modern Windows consoles support Unicode
        if (System.getenv("WT_SESSION") != null || System.getenv("WT_PROFILE_ID") != null) {
            return true;
        }

        // ConEmu supports Unicode
        if (System.getenv("ConEmuPID") != null) {
            return true;
        }

        // On Windows, fallback to checking if Windows 10+ (which has better Unicode support)
        // but be conservative - default to false unless we can confirm support
        return false;
    }

    /**
     * <p>Returns true if the terminal supports OSC 9;4 taskbar progress sequences.</p>
     *
     * <p>This sequence (ESC ] 9 ; 4 ; state ; progress ST) allows applications to control
     * progress indicators in the Windows taskbar or terminal window decorations.</p>
     *
     * <p>Supported by: ConEmu, Ghostty, and potentially other terminals.</p>
     *
     * @return true if OSC 9;4 sequences are supported, false otherwise
     */
    default boolean supportsTaskbarProgress() {
        // ConEmu explicitly supports OSC 9;4 sequences
        if (System.getenv("ConEmuPID") != null) {
            return true;
        }

        // Ghostty supports OSC 9;4 sequences
        String term = System.getenv("TERM");
        return term != null && term.toLowerCase(Locale.ROOT).contains("ghostty");
    }
}
