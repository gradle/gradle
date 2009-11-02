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
package org.gradle.api.file;

import org.gradle.util.GUtil;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * Represents a relative path from a base directory to a file.  Used in file copying to
 * represent both a source and target file path when copying files.
 *
 * @author Steve Appling
 */
public class RelativePath {
    private final boolean endsWithFile;
    private final String[] segments;

    /**
     * CTOR
     * @param endsWithFile - if true, the path ends with a file, otherwise a directory
     * @param segments
     */
    public RelativePath(boolean endsWithFile, String... segments) {
        this(endsWithFile, null, segments);
    }

    public RelativePath(boolean endsWithFile, RelativePath parentPath, String... children) {
        this.endsWithFile = endsWithFile;
        int sourceLength = 0;
        if (parentPath != null) {
            String[] sourceSegments = parentPath.getSegments();
            sourceLength = sourceSegments.length;
            segments = new String[sourceLength + children.length];
            System.arraycopy(sourceSegments, 0, segments, 0, sourceLength);
        } else {
            segments = new String[children.length];
        }
        System.arraycopy(children, 0, segments, sourceLength, children.length);
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
        StringBuilder result = new StringBuilder();
        for (int i=0; i<segments.length; i++) {
            result.append(segments[i]);
            if (i < segments.length-1) {
                result.append("/");
            }
        }
        return result.toString();
    }

    public File getFile(File baseDir) {
        return new File(baseDir, getPathString());
    }

    public String getLastName() {
        if (segments.length > 0) {
            return segments[segments.length-1];
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RelativePath that = (RelativePath) o;

        if (endsWithFile != that.endsWithFile) return false;
        if (!Arrays.equals(segments, that.segments)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (endsWithFile ? 1 : 0);
        result = 31 * result + Arrays.hashCode(segments);
        return result;
    }

    @Override
    public String toString() {
        return GUtil.join(segments, "/");
    }

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
}
