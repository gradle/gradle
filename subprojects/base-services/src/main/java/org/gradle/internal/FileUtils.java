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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class FileUtils {
    public static final int WINDOWS_PATH_LIMIT = 260;

    /**
     * Followings are reserved characters by windows:
     * <ul>
     * <li>&lt; (less than)</li>
     * <li>&gt; (greater than)</li>
     * <li>: (colon)</li>
     * <li>" (double quote)</li>
     * <li>/ (forward slash)</li>
     * <li>\ (backslash)</li>
     * <li>| (vertical bar or pipe)</li>
     * <li>? (question mark)</li>
     * <li>* (asterisk)</li>
     * </ul>
     * <p>
     * ¥ (yen sign) is escaped for security reasons:
     * <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/dd374047(v=vs.85).aspx#SC_char_sets_in_file_names">Security Considerations for Character Sets in File Names</a>
     * <p>
     * # (number sign) is actually valid, and it is added to escape illegal characters.
     */
    private static final Set<Character> ILLEGAL_CHARS = ImmutableSet.of('\\', '/', ':', '*', '?', '"', '<', '>', '|', '\0', '¥', '#');

    /**
     * Converts a string into a string that is safe to use as a file name.
     */
    public static String toSafeFileName(String name) {
        int size = name.length();
        StringBuilder rc = new StringBuilder(size * 2);
        for (int i = 0; i < size; i++) {
            char c = name.charAt(i);
            if (!ILLEGAL_CHARS.contains(c)) {
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
     * Canonializes the given file.
     */
    public static File canonicalize(File src) {
        try {
            return src.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
