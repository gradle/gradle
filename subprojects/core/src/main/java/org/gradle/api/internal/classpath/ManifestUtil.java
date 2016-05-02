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
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

public class ManifestUtil {
    private static final String[] EMPTY = new String[0];

    public static String createManifestClasspath(File jarFile, Collection<File> classpath) {
        List<String> paths = new ArrayList<String>(classpath.size());
        for (File file : classpath) {
            String path = constructRelativeClasspathUri(jarFile, file);
            paths.add(path);
        }

        return CollectionUtils.join(" ", paths);
    }

    private static String constructRelativeClasspathUri(File jarFile, File file) {
        URI jarFileUri = jarFile.getParentFile().toURI();
        URI fileUri = file.toURI();
        URI relativeUri = jarFileUri.relativize(fileUri);
        return relativeUri.getRawPath();
    }

    public static List<URI> parseManifestClasspath(File jarFile) {
        List<URI> manifestClasspath = new ArrayList<URI>();
        for (String value : readManifestClasspathString(jarFile)) {
            try {
                URI uri = new URI(value);
                uri = jarFile.toURI().resolve(uri);
                manifestClasspath.add(uri);
            } catch (URISyntaxException e) {
                throw new UncheckedIOException(e);
            }
        }
        return manifestClasspath;
    }

    private static String[] readManifestClasspathString(File classpathFile) {
        try {
            Manifest manifest = findManifest(classpathFile);
            if (manifest == null) {
                return EMPTY;
            }
            String classpathEntry = manifest.getMainAttributes().getValue("Class-Path");
            if (classpathEntry == null || classpathEntry.trim().length() == 0) {
                return EMPTY;
            }
            return classpathEntry.split(" ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /*
     * The manifest if this is a jar file and has a manifest, null otherwise.
     */
    private static Manifest findManifest(File possibleJarFile) throws IOException {
        if (!possibleJarFile.exists() || !possibleJarFile.isFile()) {
            return null;
        }
        JarFile jarFile;
        try {
            jarFile = new JarFile(possibleJarFile);
        } catch (ZipException e) {
            // Not a zip file
            return null;
        }
        try {
            return jarFile.getManifest();
        } finally {
            jarFile.close();
        }
    }
}
