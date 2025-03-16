/*
 * Copyright 2007 the original author or authors.
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

import com.google.common.base.Strings;
import com.google.common.collect.AbstractIterator;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.StringUtils.join;

/**
 * Represents a path in Gradle.
 */
public class Path implements Comparable<Path> {
    public static final Path ROOT = new Path(new String[0], true);

    private static final Comparator<String> STRING_COMPARATOR = GUtil.caseInsensitive();
    public static final String SEPARATOR = ":";

    public static Path path(@Nullable String path) {
        validatePath(path);
        if (SEPARATOR.equals(path)) {
            return ROOT;
        } else {
            return parsePath(path);
        }
    }

    /**
     * throws if no path is specified
     *
     * @since 8.5
     */
    @Incubating
    public static void validatePath(@Nullable String path) {
        if (Strings.isNullOrEmpty(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
    }

    private static Path parsePath(String path) {
        String[] segments = StringUtils.split(path, SEPARATOR);
        boolean absolute = path.startsWith(SEPARATOR);
        return new Path(segments, absolute);
    }

    private final String[] segments;
    private final boolean absolute;
    private final int hashCode;
    private volatile String fullPath;

    private Path(String[] segments, boolean absolute) {
        this.segments = segments;
        this.absolute = absolute;

        this.hashCode = computeHashCode(absolute, segments);
    }

    @Override
    public String toString() {
        return getPath();
    }

    /**
     * Appends the supplied path to this path, returning the new path.
     * The resulting path with be absolute or relative based on the path being appended _to_.
     * It makes no difference if the _appended_ path is absolute or relative.
     *
     * <pre>
     * path(':a:b').append(path(':c:d')) == path(':a:b:c:d')
     * path(':a:b').append(path('c:d')) == path(':a:b:c:d')
     * path('a:b').append(path(':c:d')) == path('a:b:c:d')
     * path('a:b').append(path('c:d')) == path('a:b:c:d')
     * </pre>
     */
    public Path append(Path path) {
        if (path.segments.length == 0) {
            return this;
        }
        String[] concat = new String[segments.length + path.segments.length];
        System.arraycopy(segments, 0, concat, 0, segments.length);
        System.arraycopy(path.segments, 0, concat, segments.length, path.segments.length);
        return new Path(concat, absolute);
    }

    public String getPath() {
        if (fullPath == null) {
            fullPath = createFullPath();
        }
        return fullPath;
    }

    private String createFullPath() {
        StringBuilder path = new StringBuilder();
        if (absolute) {
            path.append(SEPARATOR);
        }
        return path.append(join(segments, SEPARATOR)).toString();
    }

    /**
     * returns an immutable list of the segments of this path
     *
     * @since 8.5
     */
    @Incubating
    public List<String> segments(){
        return asList(segments);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Path path = (Path) o;

        if (absolute != path.absolute) {
            return false;
        }
        return Arrays.equals(segments, path.segments);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int computeHashCode(boolean absolute, String[] segments) {
        int result = 0;
        for (Object element : segments) {
            result = 31 * result + element.hashCode();
        }
        result = 31 * result + (absolute ? 1 : 0);
        return result;
    }

    @Override
    public int compareTo(Path other) {
        if (absolute && !other.absolute) {
            return 1;
        }
        if (!absolute && other.absolute) {
            return -1;
        }
        for (int i = 0; i < Math.min(segments.length, other.segments.length); i++) {
            int diff = STRING_COMPARATOR.compare(segments[i], other.segments[i]);
            if (diff != 0) {
                return diff;
            }
        }
        int lenDiff = segments.length - other.segments.length;
        if (lenDiff > 0) {
            return 1;
        }
        if (lenDiff < 0) {
            return -1;
        }
        return 0;
    }

    /**
     * Returns the parent of this path, or null if this path has no parent.
     *
     * @return The parent of this path.
     */
    @Nullable
    public Path getParent() {
        if (segments.length == 0) {
            return null;
        }
        if (segments.length == 1) {
            return absolute ? ROOT : null;
        }
        String[] parentPath = new String[segments.length - 1];
        System.arraycopy(segments, 0, parentPath, 0, parentPath.length);
        return new Path(parentPath, absolute);
    }

    /**
     * Returns the base name of this path, or null if this path is the root path.
     *
     * @return The base name,
     */
    @Nullable
    public String getName() {
        if (segments.length == 0) {
            return null;
        }
        return segments[segments.length - 1];
    }

    /**
     * Creates a child of this path with the given name.
     */
    public Path child(String name) {
        String[] childSegments = new String[segments.length + 1];
        System.arraycopy(segments, 0, childSegments, 0, segments.length);
        childSegments[segments.length] = name;
        return new Path(childSegments, absolute);
    }

    /**
     * Resolves the given name relative to this path. If an absolute path is provided, it is returned.
     */
    public String absolutePath(String path) {
        return absolutePath(path(path)).getPath();
    }

    public Path absolutePath(Path path) {
        if (path.absolute) {
            return path;
        }
        return append(path);
    }


    public boolean isAbsolute() {
        return absolute;
    }

    /**
     * Calculates a path relative to this path. If the given path is not a child of this path, it is returned unmodified.
     */
    public String relativePath(String path) {
        return relativePath(path(path)).getPath();
    }

    public Path relativePath(Path path) {
        if (path.absolute != absolute) {
            return path;
        }
        if (path.segments.length < segments.length) {
            return path;
        }
        for (int i = 0; i < segments.length; i++) {
            if (!path.segments[i].equals(segments[i])) {
                return path;
            }
        }
        if (path.segments.length == segments.length) {
            return path;
        }
        return new Path(Arrays.copyOfRange(path.segments, segments.length, path.segments.length), false);
    }

    public int segmentCount() {
        return segments.length;
    }

    public Path removeFirstSegments(int n) {
        if (n == 0) {
            return this;
        } else if (n == segments.length && absolute) {
            return ROOT;
        } else if (n < 0 || n >= segments.length) {
            throw new IllegalArgumentException("Cannot remove " + n + " segments from path " + getPath());
        }

        return new Path(Arrays.copyOfRange(segments, n, segments.length), absolute);
    }

    public String segment(int index) {
        if (index < 0 || index >= segments.length) {
            throw new IllegalArgumentException("Segment index " + index + " is invalid for path " + getPath());
        }

        return segments[index];
    }

    /**
     * Returns a {@code Path} containing only the first {@code n} segments of this {@code Path}.
     *
     * The returned {@code Path} will be absolute if this {@code Path} is absolute.
     *
     * @param n number of segments to take from this Path, must be greater than or equal to 1
     * @since 8.4
     */
    @Incubating
    public Path takeFirstSegments(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("Taken path segment count must be >= 1.");
        }
        if (n >= segmentCount()) {
            return this;
        }
        return new Path(Arrays.copyOfRange(segments, 0, Math.min(n, segmentCount())), absolute);
    }

    /**
     * Iterate over all ancestors of this path, starting with the root path (if absolute) or the first segment (if relative) and not including this path.
     *
     * <p>
     * For example, the path {@code :a:b:c} has the ancestors {@code :}, {@code :a}, and {@code :a:b}.
     * The path {@code a:b:c} has the ancestors {@code a}, and {@code a:b}.
     * </p>
     *
     * @since 8.13
     */
    @Incubating
    public Iterable<Path> ancestors() {
        return new Iterable<Path>() {
            @Override
            public Iterator<Path> iterator() {
                return new AbstractIterator<Path>() {
                    private int segmentsToInclude = absolute ? 0 : 1;

                    @Nullable
                    @Override
                    protected Path computeNext() {
                        if (segmentsToInclude >= segments.length) {
                            return endOfData();
                        }
                        if (segmentsToInclude == 0) {
                            segmentsToInclude++;
                            return Path.ROOT;
                        }
                        int segments = segmentsToInclude;
                        segmentsToInclude++;
                        return takeFirstSegments(segments);
                    }
                };
            }
        };
    }
}
