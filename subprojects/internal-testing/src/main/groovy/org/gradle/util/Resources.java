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
package org.gradle.util;

import org.gradle.test.fixtures.file.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A JUnit rule which helps locate test resources.
 */
public class Resources implements MethodRule {
    private Class<?> testClass;

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
     * @return the resource, or null if not found.
     */
    public TestFile findResource(String name) {
        assertNotNull(testClass);
        URL resource = testClass.getResource(name);
        if (resource == null) {
            return null;
        }
        assertEquals(String.format("Cannot handle resource URI %s", resource), "file", resource.getProtocol());
        File file;
        try {
            file = new File(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return new TestFile(file);
    }

    public Statement apply(final Statement statement, FrameworkMethod frameworkMethod, Object o) {
        testClass = frameworkMethod.getMethod().getDeclaringClass();
        return statement;
    }
}
