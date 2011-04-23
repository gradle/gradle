/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util;

public class TextUtil {
    /**
     * Returns the line separator for Windows.
     */
    public static String getWindowsLineSeparator() {
        return "\r\n";
    }

    /**
     * Returns the line separator for Unix.
     */
    public static String getUnixLineSeparator() {
        return "\n";
    }

    /**
     * Returns the line separator for this platform.
     */
    public static String getPlatformLineSeparator() {
        return SystemProperties.getLineSeparator();
    }

    /**
     * Converts all line separators in the specified string to the specified line separator.
     */
    public static String convertLineSeparators(String str, String sep) {
        return str.replaceAll("\r\n|\r|\n", sep);
    }

    /**
     * Converts all line separators in the specified string to the the platform's line separator.
     */
    public static String toPlatformLineSeparators(String str) {
        return convertLineSeparators(str, getPlatformLineSeparator());
    }
}
