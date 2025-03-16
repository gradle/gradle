/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Objects;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Path Factory.
 */
public class PathFactory {

    private final List<Variable> variables = new ArrayList<>();
    private final Map<String, File> varsByName = new HashMap<>();

    public PathFactory addPathVariable(String name, File dir) {
        variables.add(new Variable('$' + name + '$', dir.getAbsolutePath() + File.separator, dir));
        varsByName.put(name, dir);
        return this;
    }

    /**
     * Creates a path for the given file.
     */
    public FilePath path(File file) {
        return path(file, false);
    }

    /**
     * Creates a path for the given file.
     *
     * @param file The file to generate a path for
     * @param useFileScheme Whether 'file://' prefixed URI should be used even for JAR files
     */
    public FilePath path(File file, boolean useFileScheme) {
        Variable match = null;
        for (Variable variable : variables) {
            if (file.getAbsolutePath().equals(variable.getDir().getAbsolutePath())) {
                match = variable;
                break;
            }
            if (file.getAbsolutePath().startsWith(variable.getPrefix())) {
                if (match == null || variable.getPrefix().startsWith(match.getPrefix())) {
                    match = variable;
                }
            }
        }

        if (match != null) {
            return resolvePath(match.getDir(), match.getName(), file);
        }

        // IDEA doesn't like the result of file.toURI() so use the absolute path instead
        String relPath = file.getAbsolutePath().replace(File.separatorChar, '/');
        String url = relativePathToURI(relPath, useFileScheme);
        return new FilePath(file, url, url, relPath);
    }

    /**
     * Creates a path relative to the given path variable.
     */
    public FilePath relativePath(String pathVar, File file) {
        return resolvePath(varsByName.get(pathVar), "$" + pathVar + "$", file);
    }

    private static FilePath resolvePath(File rootDir, String rootDirName, File file) {
        String relPath = getRelativePath(rootDir, rootDirName, file);
        String url = relativePathToURI(relPath);
        String canonicalUrl = relativePathToURI(file.getAbsolutePath().replace(File.separatorChar, '/'));
        return new FilePath(file, url, canonicalUrl, relPath);
    }

    /**
     * Creates a path for the given URL.
     */
    public Path path(String url) {
        return path(url, null);
    }

    /**
     * Creates a path for the given URL.
     */
    public Path path(String url, String relPath) {
        try {
            String expandedUrl = url;
            for (Variable variable : variables) {
                expandedUrl = expandedUrl.replace(variable.getName(), variable.getPrefix());
            }
            if (expandedUrl.toLowerCase(Locale.ROOT).startsWith("file://")) {
                expandedUrl = toUrl("file", new File(expandedUrl.substring(7)).getCanonicalFile());
            } else if (expandedUrl.toLowerCase(Locale.ROOT).startsWith("jar://")) {
                String[] parts = expandedUrl.substring(6).split("!");
                if (parts.length == 2) {
                    expandedUrl = toUrl("jar", new File(parts[0]).getCanonicalFile()) + "!" + parts[1];
                }
            }
            return new Path(url, expandedUrl, relPath);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String toUrl(String scheme, File file) {
        return scheme + "://" + file.getAbsolutePath().replace(File.separatorChar, '/');
    }

    private static String getRelativePath(File rootDir, String rootDirString, File file) {
        String relpath = matchPathLists(getPathList(rootDir), getPathList(file));
        return relpath != null ? rootDirString + "/" + relpath : file.getAbsolutePath().replace(File.separatorChar, '/');
    }

    private static String relativePathToURI(String relpath) {
        return relativePathToURI(relpath, false);
    }

    private static String relativePathToURI(String relpath, boolean useFileScheme) {
        if (relpath.endsWith(".jar") && !useFileScheme) {
            return "jar://" + relpath + "!/";
        } else {
            return "file://" + relpath;
        }
    }

    private static List<String> getPathList(File f) {
        try {
            List<String> list = new ArrayList<>();
            File r = f.getCanonicalFile();
            while (r != null) {
                File parent = r.getParentFile();
                list.add(parent != null ? r.getName() : r.getAbsolutePath());
                r = parent;
            }
            return list;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String matchPathLists(List<String> r, List<String> f) {
        StringBuilder s = new StringBuilder();

        // eliminate the common root
        int i = r.size() - 1;
        int j = f.size() - 1;

        if (!r.get(i).equals(f.get(j))) {
            // no common root
            return null;
        }

        while (i >= 0 && j >= 0 && Objects.equal(r.get(i), f.get(j))) {
            i--;
            j--;
        }

        // for each remaining level in the relativeTo path, add a ..
        for (; i >= 0; i--) {
            s.append("../");
        }

        // for each level in the file path, add the path
        for (; j >= 1; j--) {
            s.append(f.get(j)).append("/");
        }

        // add the file name
        if (j == 0) {
            s.append(f.get(j));
        }

        return s.toString();
    }

    private static class Variable {

        private final String name;
        private final String prefix;
        private final File dir;

        Variable(String name, String prefix, File dir) {
            this.name = name;
            this.prefix = prefix;
            this.dir = dir;
        }

        public final String getName() {
            return name;
        }

        public final String getPrefix() {
            return prefix;
        }

        public final File getDir() {
            return dir;
        }
    }
}
