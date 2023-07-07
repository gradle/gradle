/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal;

import org.apache.commons.io.FileUtils;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.Assert.assertNotNull;

/**
 * A JUnit rule which helps locate test resources.
 */
public class Resources implements MethodRule {
    private static final String EXTRACTED_RESOURCES_DIR = "tmp-extracted-resources";

    /**
     * Map from (test class name + jar file name) -> temp directory containing extracted jar contents.
     *
     * Resources contained in jars will be extracted to the {@link #EXTRACTED_RESOURCES_DIR} dir in the parent of the root test directory,
     * to avoid using a temp directory and possibly leaking any information into the system temp dir.
     *
     * This is a {@code static} because of how JUnit handles instantiating the test class, to avoid extracting the same
     * jar multiple times per test class.
     */
    private final static Map<String, File> extractedJars = new ConcurrentHashMap<>();
    private final TestDirectoryProvider testDirectoryProvider;

    private Class<?> testClass;

    public Resources(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
    }

    /**
     * Locates the resource with the given name, relative to the current test class. Asserts that the resource exists.
     */
    public TestFile getResource(String name) {
        assertNotNull(testClass);
        TestFile file = findResource(name);
        assertNotNull(String.format("Could not locate resource '%s' for test class %s.", name, testClass.getName()), file);
        return file;
    }

    /**
     * Locates the resource with the given name, relative to the current test class.
     *
     * Can also handle extracting resources contained within a jar file found in the same manner.
     *
     * @return the resource, or {@code null} if not found
     */
    public TestFile findResource(String name) {
        assertNotNull(testClass);
        URL resource = testClass.getResource(name);
        if (resource == null) {
            return null;
        }

        switch (resource.getProtocol()) {
            case "jar":
                return fromWithinJar(resource);
            case "file":
                return fromFile(resource);
            default:
                throw new RuntimeException(String.format("Cannot handle resource URI %s", resource));
        }
    }

    @Nonnull
    private TestFile fromFile(URL resource) {
        try {
            return new TestFile(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracts the contents of the jar file containing the given resource so that an (unzipped) file pointing to the
     * requested resources within the resource jar can be returned.
     *
     * @param resourceUrl the URL of the resource within the jar to be extracted
     * @return an uncompressed file pointing to the extracted resource
     */
    @Nonnull
    private TestFile fromWithinJar(URL resourceUrl) {
        int indexOfJarSeparator = resourceUrl.getPath().indexOf("!/");
        String jarFilePath = resourceUrl.getPath().substring(5, indexOfJarSeparator);
        String jarFileName = new File(jarFilePath).getName();
        final File outputDir = testDirectoryProvider.getTestDirectory().getParentFile().createDir(EXTRACTED_RESOURCES_DIR, jarFileName);

        final String extractionKey = testClass.getName() + ":" + jarFilePath;
        extractedJars.computeIfAbsent(extractionKey, k -> {
            try {
                return extractJarContents(jarFilePath, outputDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        String pathWithinJar = resourceUrl.getPath().substring(indexOfJarSeparator + 2);
        return new TestFile(new File(outputDir, pathWithinJar));
    }

    private File extractJarContents(String sourceJarPath, File destDir) throws IOException {
        FileUtils.deleteDirectory(destDir);
        if (!destDir.mkdir()) {
            throw new IOException("Could not create root unzip directory " + destDir);
        }

        try (JarFile sourceJar = new JarFile(sourceJarPath)) {
            for (JarEntry sourceJarEntry : Collections.list(sourceJar.entries())) {
                File currFile = new File(destDir, sourceJarEntry.getName());
                if (sourceJarEntry.isDirectory()) {
                    if (!currFile.mkdir()) {
                        throw new IOException("Could not create directory " + currFile);
                    }
                } else {
                    try (InputStream inputStream = sourceJar.getInputStream(sourceJarEntry);
                         FileOutputStream outputStream = new FileOutputStream(currFile)) {
                        while (inputStream.available() > 0) {
                            outputStream.write(inputStream.read());
                        }
                    }
                }
            }
        }

        return destDir;
    }

    @Override
    public Statement apply(final Statement statement, FrameworkMethod frameworkMethod, Object o) {
        testClass = frameworkMethod.getMethod().getDeclaringClass();
        return statement;
    }
}
