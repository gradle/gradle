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

import org.apache.commons.lang.StringUtils;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ListIterator;

/**
 * <p>Represents a relative path from some base directory to a file.  Used in file copying to represent both a source
 * and target file path when copying files.</p>
 *
 * <p>{@code RelativePath} instances are immutable.</p>
 */
public class RelativePath implements Serializable {
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

    private RelativePath(boolean endsWithFile, RelativePath parentPath, String... childSegments) {
        this.endsWithFile = endsWithFile;
        int sourceLength = 0;
        if (parentPath != null) {
            String[] sourceSegments = parentPath.getSegments();
            sourceLength = sourceSegments.length;
            segments = new String[sourceLength + childSegments.length];
            System.arraycopy(sourceSegments, 0, segments, 0, sourceLength);
        } else {
            segments = new String[childSegments.length];
        }
        System.arraycopy(childSegments, 0, segments, sourceLength, childSegments.length);
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
        return CollectionUtils.join("/", segments);
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
        if (!Arrays.equals(segments, that.segments)) {
            return false;
        }

        return true;
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
        if (segments.length == 0) {
            return null;
        }
        String[] parentSegments = new String[segments.length - 1];
        System.arraycopy(segments, 0, parentSegments, 0, parentSegments.length);
        return new RelativePath(false, parentSegments);
    }

    public static RelativePath parse(boolean isFile, String path) {
        return parse(isFile, null, path);
    }

    public static RelativePath parse(boolean isFile, RelativePath parent, String path) {
        String[] names = StringUtils.split(path, "/" + File.separator);
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
        System.arraycopy(segments, 0, newSegments, 0, segments.length);
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
}
