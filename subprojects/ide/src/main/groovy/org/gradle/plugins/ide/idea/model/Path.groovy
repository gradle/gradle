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
package org.gradle.plugins.ide.idea.model

/**
 * Represents a path in a format as used often in ipr and iml files.
 *
 * @author Hans Dockter
 */
class Path {
    /**
     * The url of the path. Must not be null
     */
    final String url

    /**
     * The relative path of the path. Must not be null
     */
    final String relPath

    final String canonicalUrl

    Path(File rootDir, String rootDirString, File file) {
        relPath = getRelativePath(rootDir, rootDirString, file)
        url = relativePathToURI(relPath)
        canonicalUrl = relativePathToURI(file.absolutePath.replace(File.separator, '/'))
    }

    Path(File file) {
        // IDEA doesn't like the result of file.toURI() so use the absolute path instead
        relPath = file.absolutePath.replace(File.separator, '/')
        url = relativePathToURI(relPath)
        canonicalUrl = url
    }

    Path(String url) {
        this(url, url)
    }

    Path(String url, String canonicalUrl) {
        this.relPath = null
        this.url = url
        this.canonicalUrl = canonicalUrl
    }

    private static String getRelativePath(File rootDir, String rootDirString, File file) {
        String relpath = getRelativePath(rootDir, file)
        return relpath != null ? rootDirString + '/' + relpath : file.absolutePath.replace(File.separator, '/')
    }

    private static String relativePathToURI(String relpath) {
        if (relpath.endsWith('.jar')) {
            return 'jar://' + relpath + '!/';
        } else {
            return 'file://' + relpath;
        }
    }

    // This gets a relative path even if neither path is an ancestor of the other.
    // implementation taken from http://www.devx.com/tips/Tip/13737 and slighly modified
    //@param relativeTo  the destinationFile
    //@param fromFile    where the relative path starts

    private static String getRelativePath(File relativeTo, File fromFile) {
        return matchPathLists(getPathList(relativeTo), getPathList(fromFile))
    }

    private static List getPathList(File f) {
        List list = []
        File r = f.canonicalFile
        while (r != null) {
            File parent = r.parentFile
            list.add(parent ? r.name : r.absolutePath)
            r = parent
        }

        return list
    }

    private static String matchPathLists(List r, List f) {
        StringBuilder s = new StringBuilder();

        // eliminate the common root
        int i = r.size() - 1
        int j = f.size() - 1

        if (r[i] != f[j]) {
            // no common root
            return null
        }

        while ((i >= 0) && (j >= 0) && (r[i] == f[j])) {
            i--
            j--
        }

        // for each remaining level in the relativeTo path, add a ..
        for (; i >= 0; i--) {
            s.append('../')
        }

        // for each level in the file path, add the path
        for (; j >= 1; j--) {
            s.append(f[j]).append('/')
        }
        // add the file name
        if (j == 0) {
            s.append(f[j])
        }

        return s.toString()
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (o == null || getClass() != o.class) { return false }

        Path path = (Path) o;

        if (canonicalUrl != path.canonicalUrl) { return false }

        return true;
    }

    int hashCode() {
        return canonicalUrl.hashCode();
    }

    public String toString() {
        return "Path{" +
                "url='" + url + '\'' +
                ", canonicalUrl='" + canonicalUrl + '\'' +
                '}';
    }
}
