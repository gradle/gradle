/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.wrapper;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Hans Dockter
 */
public class PathAssemblerTest {
    public static final String TEST_GRADLE_USER_HOME = "someUserHome";
    private PathAssembler pathAssembler;
    private String testPath;
    private String testName;
    private String testVersion;
    private String testClassifier;

    @Before
    public void setUp() {
        pathAssembler = new PathAssembler(TEST_GRADLE_USER_HOME);
        testPath = "somepath";
        testName = "somename";
        testVersion = "someversion";
        testClassifier = "someclassifier";
    }

    @Test
    public void gradleHomeWithGradleUserHomeBase() {
        String gradleHome = pathAssembler.gradleHome(PathAssembler.GRADLE_USER_HOME_STRING, testPath, testName,
                testVersion);
        assertEquals(TEST_GRADLE_USER_HOME + "/" + testPath + "/" + testName + "-" + testVersion, gradleHome);
    }

    @Test
    public void gradleHomeWithProjectBase() {
        String gradleHome = pathAssembler.gradleHome(PathAssembler.PROJECT_STRING, testPath, testName, testVersion);
        assertEquals(currentDirPath() + "/" + testPath + "/" + testName + "-" + testVersion, gradleHome);
    }

    @Test(expected = RuntimeException.class)
    public void gradleHomeWithUnknownBase() {
        pathAssembler.gradleHome("unknownBase", testPath, testName, testVersion);
    }

    @Test
    public void distZipWithGradleUserHomeBase() {
        String gradleHome = pathAssembler.distZip(PathAssembler.GRADLE_USER_HOME_STRING, testPath, testName, testVersion, testClassifier);
        assertEquals(TEST_GRADLE_USER_HOME + "/" + testPath + "/" + testName + "-" + testVersion + "-" + testClassifier + ".zip", gradleHome);
    }

    @Test
    public void distZipWithProjectBase() {
        String gradleHome = pathAssembler.distZip(PathAssembler.PROJECT_STRING, testPath, testName, testVersion, testClassifier);
        assertEquals(currentDirPath() + "/" + testPath + "/" + testName + "-" + testVersion + "-" + testClassifier + ".zip", gradleHome);
    }

    @Test(expected = RuntimeException.class)
    public void distZipWithUnknownBase() {
        pathAssembler.distZip("unknownBase", testPath, testName, testVersion, testClassifier);
    }

    private String currentDirPath() {
        return System.getProperty("user.dir");
    }
}
