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

import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ManifestUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestUtil.class);
    private static final String[] EMPTY = new String[0];

    public static String createManifestClasspath(File jarFile, Collection<File> classpath) {
        List<String> paths = new ArrayList<String>(classpath.size());
        for (File file : classpath) {
            String path = constructRelativeClasspathUri(jarFile, file);
            paths.add(path);
        }

        String manifestClasspath = GUtil.join(paths, " ");
        // TODO:DAZ Remove this logging
        LOGGER.debug("Worker Process manifest classpath: {}", manifestClasspath);
        return manifestClasspath;
    }

    // TODO:DAZ The returned URI will only be relative if the file is contained in the jarfile directory. Otherwise, an absolute URI is returned.
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
                LOGGER.warn("Invalid URI found in manifest classpath: " + value);
                // TODO:DAZ Should we be warning here?
            }
        }
        return manifestClasspath;
    }

    private static String[] readManifestClasspathString(File classpathFile) {
        if (!classpathFile.exists() || !classpathFile.isFile()) {
            return EMPTY;
        }
        try {
            Manifest manifest = new JarFile(classpathFile).getManifest();
            if (manifest == null) {
                return EMPTY;
            }
            String classpathEntry = manifest.getMainAttributes().getValue("Class-Path");
            if (classpathEntry == null) {
                return EMPTY;
            }
            return classpathEntry.split(" ");
        } catch (IOException e) {
            LOGGER.info("Failed to read manifest Class-Path from {}: {}", classpathFile, e);
            return EMPTY;
        }
    }


}
