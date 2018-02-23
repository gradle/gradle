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
package org.gradle.api.internal.tasks.testing.detection;

import org.apache.commons.lang.text.StrBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.internal.file.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.Factory;
import org.gradle.util.JarUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class manages class file extraction from library jar files.
 */
public class ClassFileExtractionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassFileExtractionManager.class);
    private final Map<String, Set<File>> packageJarFilesMappings;
    private final Map<String, File> extractedJarClasses;
    private final Set<String> unextractableClasses;
    private final TemporaryFileProvider tempDirProvider;

    public ClassFileExtractionManager(final Factory<File> tempDirFactory) {
        assert tempDirFactory != null;
        tempDirProvider = new DefaultTemporaryFileProvider(tempDirFactory);
        packageJarFilesMappings = new HashMap<String, Set<File>>();
        extractedJarClasses = new HashMap<String, File>();
        unextractableClasses = new TreeSet<String>();
    }

    /**
     * Add all packages found in the jar file to the package <> jar(s) index.
     *
     * @param libraryJar Jar file to add to the index.
     */
    public void addLibraryJar(final File libraryJar) {
        new JarFilePackageLister().listJarPackages(libraryJar, new JarFilePackageListener() {
            public void receivePackage(String packageName) {
                Set<File> jarFiles = packageJarFilesMappings.get(packageName);
                if (jarFiles == null) {
                    jarFiles = new TreeSet<File>();
                }
                jarFiles.add(libraryJar);

                packageJarFilesMappings.put(packageName, jarFiles);
            }
        });
    }

    /**
     * Retrieve the file that contains the extracted class file. <p/> This method will extract the class file if it is
     * not extracted yet. Extracted class files are deleted on exit of the Gradle process. The same class is only
     * extracted once.
     *
     * @param className Name of the class to extract.
     * @return File that contains the extracted class file.
     */
    public File getLibraryClassFile(final String className) {
        if (unextractableClasses.contains(className)) {
            return null;
        } else {
            if (!extractedJarClasses.containsKey(className)) {
                if (!extractClassFile(className)) {
                    unextractableClasses.add(className);
                }
            }

            return extractedJarClasses.get(className);
        }
    }

    private boolean extractClassFile(final String className) {
        boolean classFileExtracted = false;

        final File extractedClassFile = tempFile();
        final String classFileName = new StrBuilder().append(className).append(".class").toString();
        final String classNamePackage = classNamePackage(className);
        final Set<File> packageJarFiles = packageJarFilesMappings.get(classNamePackage);

        File classFileSourceJar = null;

        if (packageJarFiles != null && !packageJarFiles.isEmpty()) {
            final Iterator<File> packageJarFilesIt = packageJarFiles.iterator();

            while (!classFileExtracted && packageJarFilesIt.hasNext()) {
                final File jarFile = packageJarFilesIt.next();

                try {
                    classFileExtracted = JarUtil.extractZipEntry(jarFile, classFileName, extractedClassFile);

                    if (classFileExtracted) {
                        classFileSourceJar = jarFile;
                    }
                } catch (IOException e) {
                    throw new GradleException("failed to extract class file from jar (" + jarFile + ")", e);
                }
            }

            if (classFileExtracted) {
                LOGGER.debug("extracted class {} from {}", className, classFileSourceJar.getName());

                extractedJarClasses.put(className, extractedClassFile);
            }
        } // super class not on the classpath - unable to scan parent class

        return classFileExtracted;
    }

    private String classNamePackage(final String className) {
        final int lastSlashIndex = className.lastIndexOf('/');

        if (lastSlashIndex == -1) {
            return null; // class in root package - should not happen
        } else {
            return className.substring(0, lastSlashIndex + 1);
        }
    }

    private File tempFile() {
        return tempDirProvider.createTemporaryFile("jar_extract_", "_tmp"); // Could throw UncheckedIOException
    }
}
