/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.util.internal;

import java.io.File;
import java.util.Locale;

import static java.lang.String.format;

public class PathTraversalChecker {

    /**
     * Checks the entry name for path traversal vulnerable sequences.
     *
     * This code is used for path traversal, ZipSlip and TarSlip detection.
     *
     * <b>IMPLEMENTATION NOTE</b>
     * We do it this way instead of the way recommended in https://snyk.io/research/zip-slip-vulnerability
     * for performance reasons, calling {@link File#getCanonicalPath()} is too expensive.
     *
     * @throws IllegalArgumentException if the entry contains vulnerable sequences
     */
    public static String safePathName(String name) {
        if (isUnsafePathName(name)) {
            throw new IllegalArgumentException(format("'%s' is not a safe zip entry name.", name));
        }
        return name;
    }

    public static boolean isUnsafePathName(String name) {
        return name.isEmpty()
            || name.startsWith("/")
            || name.startsWith("\\")
            || containsDirectoryNavigation(name)
            || (name.contains(":") && isWindows());
    }

    private static boolean containsDirectoryNavigation(String name) {
        if (!name.contains("..")) {
            return false;
        }
        // We have a .. but if not before a file separator or at the end, it is OK
        return name.endsWith("\\..")
            || name.contains("..\\")
            || name.endsWith("/..")
            || name.contains("../");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.US).contains("windows");
    }
}
