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

package org.gradle.process.internal.child;

import org.gradle.api.UncheckedIOException;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

class UtilityJarFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(UtilityJarFactory.class);

    /**
     * Creates an empty jar file that contains a manifest with a Class-path entry that will load the supplied classpath.
     * This can be used to circumvent command-line length limit on windows when the classpath is very long.
     * Note that the main class must be placed on the classpath explicitly, and cannot be loaded via a classpath jar.
     *
     * @param jarFile The jarFile to write to. This file must exist, and should be empty.
     * @param classpath The files that form the classpath
     */
    public void createClasspathJarFile(File jarFile, Collection<File> classpath) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Class-Path", createManifestClasspath(jarFile, classpath));
        try {
            writeManifestOnlyJarFile(jarFile, manifest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String createManifestClasspath(File jarFile, Collection<File> classpath) {
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
    private String constructRelativeClasspathUri(File jarFile, File file) {
        URI jarFileUri = jarFile.getParentFile().toURI();
        URI fileUri = file.toURI();
        URI relativeUri = jarFileUri.relativize(fileUri);
        return relativeUri.getRawPath();
    }

    private void writeManifestOnlyJarFile(File file, Manifest manifest) throws IOException {
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file));

        // Need to create the directory entry, since JarOutputStream will not create it by default.
        jarOutputStream.putNextEntry(new ZipEntry("META-INF/"));
        jarOutputStream.closeEntry();
        
        ZipEntry e = new ZipEntry(JarFile.MANIFEST_NAME);
        jarOutputStream.putNextEntry(e);
        manifest.write(new BufferedOutputStream(jarOutputStream));
        jarOutputStream.closeEntry();

        jarOutputStream.close();
    }
}
