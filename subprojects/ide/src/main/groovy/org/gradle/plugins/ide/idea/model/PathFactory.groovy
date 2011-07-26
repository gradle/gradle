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

class PathFactory {
    private final List<Map> variables = []
    private final Map<String, File> varsByName = [:]

    PathFactory addPathVariable(String name, File dir) {
        variables << [name: "\$${name}\$", prefix: dir.absolutePath + File.separator, dir: dir]
        varsByName[name] = dir
        return this
    }

    /**
     * Creates a path for the given file.
     */
    FilePath path(File file) {
        Map match = null
        for (variable in variables) {
            if (file.absolutePath == variable.dir.absolutePath) {
                match = variable
                break
            }
            if (file.absolutePath.startsWith(variable.prefix)) {
                if (!match || variable.prefix.startsWith(match.prefix)) {
                    match = variable
                }
            }
        }

        if (match) {
            return relativePath(match.dir, match.name, file)
        }

        // IDEA doesn't like the result of file.toURI() so use the absolute path instead
        def relPath = file.absolutePath.replace(File.separator, '/')
        def url = relativePathToURI(relPath)
        return new FilePath(file, url, url, relPath)
    }

    /**
     * Creates a path relative to the given path variable.
     */
    FilePath relativePath(String pathVar, File file) {
        return relativePath(varsByName[pathVar], "\$$pathVar\$", file)
    }

    private FilePath relativePath(File rootDir, String rootDirName, File file) {
        def relPath = getRelativePath(rootDir, rootDirName, file)
        def url = relativePathToURI(relPath)
        def canonicalUrl = relativePathToURI(file.absolutePath.replace(File.separator, '/'))
        return new FilePath(file, url, canonicalUrl, relPath)
    }
    /**
     * Creates a path for the given URL.
     */
    Path path(String url) {
        return path(url, null)
    }

    /**
     * Creates a path for the given URL.
     */
    Path path(String url, String relPath) {
        String expandedUrl = url
        for (variable in variables) {
            expandedUrl = expandedUrl.replace(variable.name, variable.prefix)
        }
        if (expandedUrl.toLowerCase().startsWith('file://')) {
            expandedUrl = toUrl('file', new File(expandedUrl.substring(7)).canonicalFile)
        } else if (expandedUrl.toLowerCase().startsWith('jar://')) {
            def parts = expandedUrl.substring(6).split('!')
            if (parts.length == 2) {
                expandedUrl = toUrl('jar', new File(parts[0]).canonicalFile) + '!' + parts[1]
            }
        }
        return new Path(url, expandedUrl, relPath)
    }

    private def toUrl(String scheme, File file) {
        return scheme + '://' + file.absolutePath.replace(File.separator, '/')
    }

    private static String getRelativePath(File rootDir, String rootDirString, File file) {
        String relpath = matchPathLists(getPathList(rootDir), getPathList(file))
        return relpath != null ? rootDirString + '/' + relpath : file.absolutePath.replace(File.separator, '/')
    }

    private static String relativePathToURI(String relpath) {
        if (relpath.endsWith('.jar')) {
            return 'jar://' + relpath + '!/';
        } else {
            return 'file://' + relpath;
        }
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
}
