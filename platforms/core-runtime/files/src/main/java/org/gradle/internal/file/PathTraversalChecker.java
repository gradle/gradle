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

package org.gradle.internal.file;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.lang.String.format;

public class PathTraversalChecker {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.US).contains("windows");

    /**
     * Checks the entry name for path traversal vulnerable sequences.
     *
     * This code is used for path traversal, ZipSlip and TarSlip detection.
     *
     * <b>IMPLEMENTATION NOTE</b>
     * We do it this way instead of the way recommended in <a href="https://snyk.io/research/zip-slip-vulnerability"></a>
     * for performance reasons, calling {@link File#getCanonicalPath()} is too expensive.
     *
     * @throws IllegalArgumentException if the entry contains vulnerable sequences
     */
    public static String safePathName(String name) {
        if (isUnsafePathName(name)) {
            throw new IllegalArgumentException(format("'%s' is not a safe archive entry or path name.", name));
        }
        return name;
    }

    public static boolean isUnsafePathName(String name) {
        if (name.isEmpty()) {
            return true;
        }
        if (IS_WINDOWS && name.contains(":")) {
            return true;
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            return true;
        }

        return containsDirectoryNavigation(name);
    }

    /**
     * We want to treat both '/' and '\' as path separators on all OSes.
     *
     * @param name the original path name
     * @return the path name with all separators replaced with the OS file separator
     */
    private static String osIndependentPath(String name) {
        if (File.separatorChar == '\\') {
            return name.replace('/', File.separatorChar);
        } else if (File.separatorChar == '/') {
            return name.replace('\\', File.separatorChar);
        } else {
            // Throw an error here, as we would want to add this separator to our list
            // rather than passing it through unmodified
            throw new IllegalStateException("Unknown file separator: " + File.separatorChar);
        }
    }

    private static boolean containsDirectoryNavigation(String name) {
        List<String> names = buildNamesList(name);
        for (String part : names) {
            if (part.equals("..")) {
                return true;
            }
            if (IS_WINDOWS) {
                // Directories with dots at the end will have them removed by win32 compatibility
                // We don't know what paths might be directories, so just ban any occurrence of dots at the end
                if (!part.equals(".") && part.endsWith(".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> buildNamesList(String name) {
        // We run this through File then toPath, as `name` is primarily used with new File(...) calls elsewhere
        // This ensures a consistent parsing/understanding of the path
        Path path = new File(osIndependentPath(name)).toPath();
        List<String> names = new ArrayList<>(path.getNameCount());
        for (Path part : path) {
            names.add(part.toString());
        }
        return names;
    }
}
