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
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.Assert.assertNotNull;

/**
 * A JUnit rule which helps locate test resources.
 */
public class Resources implements MethodRule {
    private static final String EXTRACTED_RESOURCES_DIR = "tmp-extracted-resources";

    /**
     * Set of keys in the form {@code <running test class name>:<jar file resource path>}.
     *
     * Resources contained in jars will be extracted to the {@link #EXTRACTED_RESOURCES_DIR} dir in the parent of the root test directory,
     * to avoid using a temp directory and possibly leaking any information into the system temp dir.  As this directory will always be
     * computed in the same way, it does not need to be tracked.
     *
     * This is a {@code static} because of how JUnit handles instantiating the test class, to avoid extracting the same
     * jar multiple times per running test class.  We use this set to avoid doing an IO operation to check if the jar has already been extracted,
     * slightly increasing the speed a test class runs, especially one with many test methods defined on itself or in its type hierarchy.
     */
    private final static Set<String> EXTRACTED_JARS = new HashSet<>();
    private final TestDirectoryProvider testDirectoryProvider;

    private Class<?> declaringTestClass;
    private Class<?> runningTestClass;

    public Resources(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
    }

    /**
     * Locates the resource with the given name, relative to the current class that declared the current test method.
     *
     * Also asserts that the resource exists.
     */
    public TestFile getResource(String name) {
        assertNotNull(declaringTestClass);
        TestFile file = findResource(name);
        assertNotNull(String.format("Could not locate resource '%s' for test class %s.", name, declaringTestClass.getName()), file);
        return file;
    }

    /**
     * Locates the resource with the given name, relative to the class that declared the current test method.
     *
     * Can also handle extracting resources contained within a jar file found in the same manner.
     *
     * @return the resource, or {@code null} if not found
     */
    public TestFile findResource(String name) {
        assertNotNull(declaringTestClass);
        URL resource = declaringTestClass.getResource(name);
        if (resource == null) {
            return null;
        }

        try {
            switch (resource.getProtocol()) {
                case "jar":
                        return fromWithinJar(resource);
                case "file":
                    return fromFile(resource);
                default:
                    throw new RuntimeException(String.format("Cannot handle resource URI %s", resource));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error finding resource: " + name, e);
        }
    }

    @Nonnull
    private TestFile fromFile(URL resource) throws URISyntaxException {
        return new TestFile(resource.toURI());
    }

    /**
     * Extracts the contents of the jar file containing the given resource so that an (unzipped) file pointing to the
     * requested resources within the resource jar can be returned.
     *
     * This method will extract the jar to a directory named {@link #EXTRACTED_RESOURCES_DIR} in the parent of the root
     * test directory for the <strong>running</strong> test class, not the declaring test class.  This ensures that if multiple
     * classes extends a common base class containing tests, the jar will be unzipped and found properly for each test class
     * that runs.
     *
     * @param resourceUrl the URL of the resource within the jar to be extracted
     */
    @Nonnull
    private TestFile fromWithinJar(URL resourceUrl) throws IOException {
        int indexOfJarSeparator = resourceUrl.getPath().indexOf("!/");
        String jarFilePath = resourceUrl.getPath().substring(5, indexOfJarSeparator);
        String jarFileName = new File(jarFilePath).getName();
        final File outputDir = testDirectoryProvider.getTestDirectory().getParentFile().createDir(EXTRACTED_RESOURCES_DIR, jarFileName);

        final String extractionKey = runningTestClass.getName() + ":" + jarFilePath;
        if (!EXTRACTED_JARS.contains(extractionKey)) {
            extractJarContents(jarFilePath, outputDir);
            EXTRACTED_JARS.add(extractionKey);
        }

        String pathWithinJar = resourceUrl.getPath().substring(indexOfJarSeparator + 2);
        return new TestFile(new File(outputDir, pathWithinJar));
    }

    private void extractJarContents(String sourceJarPath, File destDir) throws IOException {
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
                    Files.copy(sourceJar.getInputStream(sourceJarEntry), currFile.toPath());
                }
            }
        }
    }

    @Override
    public Statement apply(final Statement statement, FrameworkMethod frameworkMethod, Object target) {
        declaringTestClass = frameworkMethod.getMethod().getDeclaringClass();
        runningTestClass = target.getClass();
        return statement;
    }
}
