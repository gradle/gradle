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
package org.gradle.api.file;

import org.gradle.internal.file.FilePathUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ListIterator;

/**
 * <p>Represents a relative path from some base directory to a file.  Used in file copying to represent both a source
 * and target file path when copying files.</p>
 *
 * <p>{@code RelativePath} instances are immutable.</p>
 */
public class RelativePath implements Serializable, Comparable<RelativePath>, CharSequence {
    public static final RelativePath EMPTY_ROOT = new RelativePath(false);
    private final boolean endsWithFile;
    private final String[] segments;

    /**
     * Creates a {@code RelativePath}.
     *
     * @param endsWithFile - if true, the path ends with a file, otherwise a directory
     */
    public RelativePath(boolean endsWithFile, String... segments) {
        this(endsWithFile, null, segments);
    }

    private RelativePath(boolean endsWithFile, @Nullable RelativePath parentPath, String... childSegments) {
        this.endsWithFile = endsWithFile;
        int targetOffsetForChildSegments;
        if (parentPath != null) {
            String[] sourceSegments = parentPath.getSegments();
            segments = new String[sourceSegments.length + childSegments.length];
            copySegments(segments, sourceSegments, sourceSegments.length);
            targetOffsetForChildSegments = sourceSegments.length;
        } else {
            segments = new String[childSegments.length];
            targetOffsetForChildSegments = 0;
        }
        copyAndInternSegments(segments, targetOffsetForChildSegments, childSegments);
    }

    private static void copySegments(String[] target, String[] source) {
        copySegments(target, source, target.length);
    }

    private static void copySegments(String[] target, String[] source, int length) {
        System.arraycopy(source, 0, target, 0, length);
    }

    private static void copyAndInternSegments(String[] target, int targetOffset, String[] source) {
        System.arraycopy(source, 0, target, targetOffset, source.length);
    }

    public String[] getSegments() {
        return segments;
    }

    public ListIterator<String> segmentIterator() {
        ArrayList<String> content = new ArrayList<String>(Arrays.asList(segments));
        return content.listIterator();
    }

    public boolean isFile() {
        return endsWithFile;
    }

    public String getPathString() {
        if (segments.length == 0) {
            return "";
        }
        StringBuilder path = new StringBuilder(256);
        for (int i = 0, len = segments.length; i < len; i++) {
            if (i != 0) {
                path.append('/');
            }
            path.append(segments[i]);
        }
        return path.toString();
    }

    @Override
    public int length() {
        if (segments.length == 0) {
            return 0;
        }
        int length = segments.length - 1;
        for (String segment : segments) {
            length += segment.length();
        }
        return length;
    }

    @Override
    public char charAt(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        int remaining = index;
        int nextSegment = 0;
        while (nextSegment < segments.length) {
            String segment = segments[nextSegment];
            int length = segment.length();
            if (remaining < length) {
                return segment.charAt(remaining);
            } else if (remaining == length) {
                return '/';
            } else {
                remaining -= length + 1;
                nextSegment++;
            }
        }
        throw new IndexOutOfBoundsException(String.valueOf(index));
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return CharBuffer.wrap(this, start, end);
    }

    public File getFile(File baseDir) {
        return new File(baseDir, getPathString());
    }

    public String getLastName() {
        if (segments.length > 0) {
            return segments[segments.length - 1];
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RelativePath that = (RelativePath) o;

        if (endsWithFile != that.endsWithFile) {
            return false;
        }
        return Arrays.equals(segments, that.segments);
    }

    @Override
    public int hashCode() {
        int result = endsWithFile ? 1 : 0;
        result = 31 * result + Arrays.hashCode(segments);
        return result;
    }

    @Override
    public String toString() {
        return getPathString();
    }

    /**
     * Returns the parent of this path.
     *
     * @return The parent of this path, or null if this is the root path.
     */
    public RelativePath getParent() {
        switch (segments.length) {
            case 0:
                return null;
            case 1:
                return EMPTY_ROOT;
            default:
                String[] parentSegments = new String[segments.length - 1];
                copySegments(parentSegments, segments);
                return new RelativePath(false, parentSegments);
        }
    }

    public static RelativePath parse(boolean isFile, String path) {
        return parse(isFile, null, path);
    }

    public static RelativePath parse(boolean isFile, @Nullable RelativePath parent, String path) {
        String[] names = FilePathUtil.getPathSegments(path);
        return new RelativePath(isFile, parent, names);
    }

    /**
     * <p>Returns a copy of this path, with the last name replaced with the given name.</p>
     *
     * @param name The name.
     * @return The path.
     */
    public RelativePath replaceLastName(String name) {
        String[] newSegments = new String[segments.length];
        copySegments(newSegments, segments, segments.length - 1);
        newSegments[segments.length - 1] = name;
        return new RelativePath(endsWithFile, newSegments);
    }

    /**
     * <p>Appends the given path to the end of this path.
     *
     * @param other The path to append
     * @return The new path
     */
    public RelativePath append(RelativePath other) {
        return new RelativePath(other.endsWithFile, this, other.segments);
    }

    /**
     * <p>Appends the given path to the end of this path.
     *
     * @param other The path to append
     * @return The new path
     */
    public RelativePath plus(RelativePath other) {
        return append(other);
    }

    /**
     * Appends the given names to the end of this path.
     *
     * @param segments The names to append.
     * @param endsWithFile when true, the new path refers to a file.
     * @return The new path.
     */
    public RelativePath append(boolean endsWithFile, String... segments) {
        return new RelativePath(endsWithFile, this, segments);
    }

    /**
     * Prepends the given names to the start of this path.
     *
     * @param segments The names to prepend
     * @return The new path.
     */
    public RelativePath prepend(String... segments) {
        return new RelativePath(false, segments).append(this);
    }

    @Override
    public int compareTo(RelativePath o) {
        int len1 = segments.length;
        int len2 = o.segments.length;

        if (len1 != len2) {
            return len1 - len2;
        }

        int lim = Math.min(len1, len2);
        String[] v1 = segments;
        String[] v2 = o.segments;

        int k = 0;
        while (k < lim) {
            String c1 = v1[k];
            String c2 = v2[k];
            int compareResult = c1 == c2 ? 0 : c1.compareTo(c2);
            if (compareResult != 0) {
                return compareResult;
            }
            k++;
        }
        return 0;
    }
}
