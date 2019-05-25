/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.fixtures;

import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.Resources;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

/**
 * Provides access to test resources for integration testing. Looks for the following directory in the test classpath:
 * <ul> <li>${testClass}/shared</li> <li>${testClass}/${testName}</li> </ul>
 *
 * Copies the contents of each such directory into a temporary directory for the test to use.
 */
public class TestResources implements MethodRule {
    private final Logger logger = LoggerFactory.getLogger(TestResources.class);
    private final TestDirectoryProvider testWorkDirProvider;
    private final Collection<String> extraResources;
    private final Resources resources = new Resources();

    public TestResources(TestDirectoryProvider testDirectoryProvider, String... extraResources) {
        testWorkDirProvider = testDirectoryProvider;
        this.extraResources = Arrays.asList(extraResources);
    }

    public TestFile getDir() {
        return testWorkDirProvider.getTestDirectory();
    }

    @Override
    public Statement apply(Statement base, final FrameworkMethod method, Object target) {
        final Statement statement = resources.apply(base, method, target);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String className = method.getMethod().getDeclaringClass().getSimpleName();
                maybeCopy(String.format("%s/shared", className));
                maybeCopy(String.format("%s/%s", className, method.getName()));
                for (String extraResource : extraResources) {
                    maybeCopy(extraResource);
                }
                statement.evaluate();
            }
        };
    }

    /**
     * Copies the given resource to the test directory.
     */
    public void maybeCopy(String resource) {
        TestFile dir = resources.findResource(resource);
        if (dir != null) {
            logger.debug("Copying test resource '{}' from {} to test directory.", resource, dir);
            dir.copyTo(getDir());
        } else {
            logger.debug("Test resource '{}' not found, skipping.", resource);
        }
    }
}
