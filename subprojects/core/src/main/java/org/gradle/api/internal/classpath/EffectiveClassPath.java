/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.classpath;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classpath.DefaultClassPath;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class EffectiveClassPath extends DefaultClassPath {
    public EffectiveClassPath(ClassLoader classLoader) {
        super(ImmutableUniqueList.of(findAvailableClasspathFiles(classLoader)));
    }

    private static List<File> findAvailableClasspathFiles(ClassLoader classLoader) {
        List<URL> rawClasspath = ClasspathUtil.getClasspath(classLoader).getAsURLs();
        List<File> classpathFiles = new ArrayList<File>();
        for (URL url : rawClasspath) {
            if (url.getProtocol().equals("file")) {
                try {
                    File classpathFile = new File(url.toURI());
                    addClasspathFile(classpathFile, classpathFiles);
                } catch (URISyntaxException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        // The file names passed to -cp are canonicalised by the JVM when it creates the system classloader, and so the file names are
        // lost if they happen to refer to links, for example, into the Gradle artifact cache. Try to reconstitute the file names
        // from the system classpath
        if (classLoader == ClassLoader.getSystemClassLoader()) {
            for (String value : System.getProperty("java.class.path").split(File.pathSeparator)) {
                addClasspathFile(new File(value), classpathFiles);
            }
        }

        return classpathFiles;
    }

    private static void addClasspathFile(File classpathFile, List<File> classpathFiles) {
        if (classpathFile.exists() && !classpathFiles.contains(classpathFile)) {
            classpathFiles.add(classpathFile.getAbsoluteFile());
            addManifestClasspathFiles(classpathFile, classpathFiles);
        }
    }

    private static void addManifestClasspathFiles(File classpathFile, List<File> classpathFiles) {
        List<URI> classpathUris = ManifestUtil.parseManifestClasspath(classpathFile);
        for (URI classpathUri : classpathUris) {
            addClasspathFile(new File(classpathUri), classpathFiles);
        }
    }
}
