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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;

import java.util.Arrays;
import java.util.Comparator;

public class Path implements Comparable<Path> {
    private static final Comparator<String> STRING_COMPARATOR = GUtil.caseInsensitive();
    private final String[] segments;
    private final boolean absolute;
    private final String fullPath;
    public static final Path ROOT = new Path(Project.PATH_SEPARATOR);

    private Path(String path) {
        this(StringUtils.split(path, Project.PATH_SEPARATOR), path.startsWith(Project.PATH_SEPARATOR), path.length() > 1 && path.endsWith(Project.PATH_SEPARATOR) ? path.substring(0, path.length() - 1) : path);
    }

    private Path(String[] segments, boolean absolute) {
        this(segments, absolute, createFullPath(segments, absolute));
    }

    private Path(String[] segments, boolean absolute, String fullPath) {
        this.segments = segments;
        this.absolute = absolute;
        this.fullPath = fullPath;
    }

    public static Path path(String path) {
        return path.equals(Project.PATH_SEPARATOR) ? ROOT : new Path(path);
    }

    @Override
    public String toString() {
        return getPath();
    }

    public String getPath() {
        return fullPath;
    }

    private static String createFullPath(String[] segments, boolean absolute) {
        StringBuilder path = new StringBuilder();
        if (absolute) {
            path.append(Project.PATH_SEPARATOR);
        }
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                path.append(Project.PATH_SEPARATOR);
            }
            String segment = segments[i];
            path.append(segment);
        }
        return path.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        Path other = (Path) obj;
        return Arrays.equals(segments, other.segments);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(segments);
    }

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
    public Path resolve(String path) {
        return new Path(absolutePath(path));
    }

    /**
     * Resolves the given name relative to this path. If an absolute path is provided, it is returned.
     */
    public String absolutePath(String path) {
        if (!isAbsolutePath(path)) {
            return fullPath.equals(Project.PATH_SEPARATOR) ? fullPath + path : fullPath + Project.PATH_SEPARATOR + path;
        }
        return path;
    }

    /**
     * Calculates a path relative to this path. If the given path is not a child of this path, it is returned unmodified.
     */
    public String relativePath(String path) {
        if (fullPath.equals(Project.PATH_SEPARATOR)) {
            if (path.startsWith(fullPath) && path.length() > fullPath.length()) {
                return path.substring(1);
            }
            return path;
        }
        if (path.startsWith(fullPath) && path.length() > fullPath.length() + 1 && path.charAt(fullPath.length()) == ':') {
            return path.substring(fullPath.length() + 1);
        }
        return path;
    }

    private boolean isAbsolutePath(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        return path.startsWith(Project.PATH_SEPARATOR);
    }
}
