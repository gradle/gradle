/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class FileUtils {
    public static final int WINDOWS_PATH_LIMIT = 260;

    /**
     * Converts a string into a string that is safe to use as a file name. The result will only include ascii characters and numbers, and the "-","_", #, $ and "." characters.
     */
    public static String toSafeFileName(String name) {
        int size = name.length();
        StringBuffer rc = new StringBuffer(size * 2);
        for (int i = 0; i < size; i++) {
            char c = name.charAt(i);
            boolean valid = c >= 'a' && c <= 'z';
            valid = valid || (c >= 'A' && c <= 'Z');
            valid = valid || (c >= '0' && c <= '9');
            valid = valid || (c == '_') || (c == '-') || (c == '.') || (c == '$');
            if (valid) {
                rc.append(c);
            } else {
                // Encode the character using hex notation
                rc.append('#');
                rc.append(Integer.toHexString(c));
            }
        }
        return rc.toString();
    }

    public static File assertInWindowsPathLengthLimitation(File file) {
        if (file.getAbsolutePath().length() > WINDOWS_PATH_LIMIT) {
            throw new GradleException(String.format("Cannot create file. '%s' exceeds windows path limitation of %d character.", file.getAbsolutePath(), WINDOWS_PATH_LIMIT));

        }
        return file;
    }

    /**
     * Returns the outer most files that encompass the given files inclusively.
     * <p>
     * This method does not access the file system.
     * It is agnostic to whether a given file object represents a regular file, directory or does not exist.
     * That is, the term “file” is used in the java.io.File sense, not the regular file sense.
     *
     * @param files the site of files to find the encompassing roots of
     * @return the encompassing roots
     */
    public static Collection<? extends File> calculateRoots(Iterable<? extends File> files) {
        List<File> roots = new LinkedList<File>();

        files:
        for (File file : files) {
            File absoluteFile = file.getAbsoluteFile();
            String path = absoluteFile + File.separator;
            Iterator<File> rootsIterator = roots.iterator();

            while (rootsIterator.hasNext()) {
                File root = rootsIterator.next();
                String rootPath = root.getPath() + File.separator;
                if (path.startsWith(rootPath)) { // is lower than root
                    continue files;
                }

                if (rootPath.startsWith(path)) { // is higher than root
                    rootsIterator.remove();
                }
            }

            roots.add(absoluteFile);
        }

        return roots;
    }

    /**
     * Checks if the given file path ends with the given extension.
     * @param file the file
     * @param extension candidate extension including leading dot
     * @return true if {@code file.getPath().endsWith(extension)}
     */
    public static boolean hasExtension(File file, String extension) {
        return file.getPath().endsWith(extension);
    }

    /**
     * Checks if the given file path ends with the given extension (ignoring case).
     * @param fileName the file name
     * @param extension candidate extension including leading dot
     * @return true if the file name ends with extension, ignoring case
     */
    public static boolean hasExtensionIgnoresCase(String fileName, String extension) {
        return endsWithIgnoreCase(fileName, extension);
    }

    private static boolean endsWithIgnoreCase(String subject, String suffix) {
        return subject.regionMatches(true, subject.length() - suffix.length(), suffix, 0, suffix.length());
    }

    /**
     * Canonicalizes the given file.
     */
    public static File canonicalize(File src) {
        try {
            return src.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
