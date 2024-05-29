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

import com.google.common.collect.Lists;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FileUtils {
    public static final int WINDOWS_PATH_LIMIT = 260;

    private static final Comparator<File> FILE_SEGMENT_COMPARATOR = new Comparator<File>() {
        @Override
        public int compare(File left, File right) {
            String leftPath = left.getPath();
            String rightPath = right.getPath();

            int len1 = leftPath.length();
            int len2 = rightPath.length();
            int lim = Math.min(len1, len2);

            int k = 0;
            while (k < lim) {
                char c1 = leftPath.charAt(k);
                char c2 = rightPath.charAt(k);
                if (c1 != c2) {
                    if (c1 == File.separatorChar) {
                        return -1;
                    }
                    if (c2 == File.separatorChar) {
                        return 1;
                    }
                    return c1 - c2;
                }
                k++;
            }

            return len1 - len2;
        }
    };

    /**
     * Converts a string into a string that is safe to use as a file name. The result will only include ascii characters and numbers, and the "-","_", #, $ and "." characters.
     */
    public static String toSafeFileName(String name) {
        int size = name.length();
        StringBuilder rc = new StringBuilder(size * 2);
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
        List<File> sortedFiles = Lists.newArrayList(files);
        Collections.sort(sortedFiles, FILE_SEGMENT_COMPARATOR);
        List<File> result = Lists.newArrayListWithExpectedSize(sortedFiles.size());

        File currentRoot = null;
        for (File file : sortedFiles) {
            if (currentRoot == null || !doesPathStartWith(file.getPath(), currentRoot.getPath())) {
                result.add(file);
                currentRoot = file;
            }
        }
        return result;
    }

    /**
     * Checks if one path is a prefix to another.
     * <p>
     * Conceptually, it appends a file separator to both paths before doing a prefix check.
     *
     * @param path a path to check a prefix against, without a trailing file separator
     * @param startsWithPath a prefix path without a trailing file separator
     * @return true if the path starts with the prefix
     */
    public static boolean doesPathStartWith(String path, String startsWithPath) {
        if (!path.startsWith(startsWithPath)) {
            return false;
        }

        return path.length() == startsWithPath.length()
            || path.charAt(startsWithPath.length()) == File.separatorChar;
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
     * Returns a representation of the file path with an alternate extension.  If the file path has no extension,
     * then the provided extension is simply concatenated.  If the file path has an extension, the extension is
     * stripped and replaced with the provided extension.
     *
     * e.g. with a provided extension of ".bar"
     * foo -&gt; foo.bar
     * foo.baz -&gt; foo.bar
     *
     * @param filePath the file path to transform
     * @param extension the extension to use in the transformed path
     * @return the transformed path
     */
    public static String withExtension(String filePath, String extension) {
        if (filePath.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return filePath;
        }
        return removeExtension(filePath) + extension;
    }

    /**
     * Removes the extension (if any) from the file path.  If the file path has no extension, then it returns the same string.
     *
     * @return the file path without an extension
     */
    public static String removeExtension(String filePath) {
        int fileNameStart = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        int extensionPos = filePath.lastIndexOf('.');

        if (extensionPos > fileNameStart) {
            return filePath.substring(0, extensionPos);
        }
        return filePath;
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

    /**
     * Normalizes the given file, removing redundant segments like /../. If normalization
     * tries to step beyond the file system root, the root is returned.
     */
    public static File normalize(File src) {
        String path = src.getAbsolutePath();
        String normalizedPath = FilenameUtils.normalizeNoEndSeparator(path);
        if (normalizedPath != null) {
            return new File(normalizedPath);
        }
        File root = src;
        File parent = root.getParentFile();
        while (parent != null) {
            root = root.getParentFile();
            parent = root.getParentFile();
        }
        return root;
    }

}
