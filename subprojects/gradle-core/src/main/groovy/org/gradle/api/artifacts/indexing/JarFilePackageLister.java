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

package org.gradle.api.artifacts.indexing;

import org.gradle.api.GradleException;

import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.io.File;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class JarFilePackageLister {
    public void listJarPackages(File jarFile, JarFilePackageListener listener) {
        if ( jarFile == null ) throw new IllegalArgumentException("jarFile is null!");

        final String jarFileAbsolutePath = jarFile.getAbsolutePath();

        if ( !jarFile.exists() ) throw new IllegalArgumentException("jarFile doesn't exists! (" + jarFileAbsolutePath + ")");
        if ( !jarFile.isFile() ) throw new IllegalArgumentException("jarFile is not a file! (" + jarFileAbsolutePath + ")");
        if ( !jarFile.getName().endsWith(".jar") ) throw new IllegalArgumentException("jarFile is not a jarFile! (" + jarFileAbsolutePath + ")");

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(jarFile);

            final Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();

            while ( zipFileEntries.hasMoreElements() ) {
                final ZipEntry zipFileEntry = zipFileEntries.nextElement();

                if ( zipFileEntry.isDirectory() ) {
                    final String zipFileEntryName = zipFileEntry.getName();

                    if ( !zipFileEntryName.startsWith("META-INF") )
                        listener.receivePackage(zipFileEntryName);
                }
            }
        }
        catch (IOException e) {
            throw new GradleException("failed to scan jar file for packages (" + jarFileAbsolutePath + ")", e);
        }
        finally {
            if ( zipFile != null ) {
                try {
                    zipFile.close();
                }
                catch ( IOException e ) {
                    // ignore
                }
            }
        }
    }
}