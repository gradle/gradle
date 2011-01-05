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

import org.junit.Test;

import java.io.File;
import java.net.URI;

import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class PathAssemblerTest {
    public static final String TEST_GRADLE_USER_HOME = "someUserHome";
    private PathAssembler pathAssembler = new PathAssembler(TEST_GRADLE_USER_HOME);

    @Test
    public void gradleHomeWithGradleUserHomeBase() throws Exception {
        File gradleHome = pathAssembler.gradleHome(PathAssembler.GRADLE_USER_HOME_STRING, "somePath", new URI("http://server/dist/gradle-0.9-bin.zip"));
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9"), gradleHome);
    }

    @Test
    public void gradleHomeWithProjectBase() throws Exception {
        File gradleHome = pathAssembler.gradleHome(PathAssembler.PROJECT_STRING, "somePath", new URI("http://server/dist/gradle-0.9-bin.zip"));
        assertEquals(file(currentDirPath() + "/somePath/gradle-0.9"), gradleHome);
    }

    @Test
    public void gradleHomeForUriWithNoPath() throws Exception {
        File gradleHome = pathAssembler.gradleHome(PathAssembler.GRADLE_USER_HOME_STRING, "somePath", new URI("http://server/gradle-0.9-bin.zip"));
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9"), gradleHome);
    }

    @Test
    public void gradleHomeForSnapshotVersion() throws Exception {
        File gradleHome = pathAssembler.gradleHome(PathAssembler.GRADLE_USER_HOME_STRING, "somePath", new URI("http://server/gradle-0.9-some-branch-2010+1100-bin.zip"));
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9-some-branch-2010+1100"), gradleHome);
    }

    @Test
    public void gradleHomeForUrlWithNoClassifier() throws Exception {
        File gradleHome = pathAssembler.gradleHome(PathAssembler.GRADLE_USER_HOME_STRING, "somePath", new URI("http://server/gradle-0.9.zip"));
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9"), gradleHome);
    }

    @Test
    public void gradleHomeForUrlWithNoVersion() throws Exception {
        try {
            pathAssembler.gradleHome(PathAssembler.GRADLE_USER_HOME_STRING, "somePath", new URI("http://server/gradle-bin.zip"));
            fail();
        } catch (RuntimeException e) {
            assertEquals("Cannot determine Gradle version from distribution URL 'http://server/gradle-bin.zip'.", e.getMessage());
        }
    }

    @Test(expected = RuntimeException.class)
    public void gradleHomeWithUnknownBase() throws Exception {
        pathAssembler.gradleHome("unknownBase", "somePath", new URI("http://server/gradle.zip"));
    }

    @Test
    public void distZipWithGradleUserHomeBase() throws Exception {
        File dist = pathAssembler.distZip(PathAssembler.GRADLE_USER_HOME_STRING, "somePath", new URI("http://server/dist/gradle.zip"));
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle.zip"), dist);
    }

    @Test
    public void distZipWithProjectBase() throws Exception {
        File dist = pathAssembler.distZip(PathAssembler.PROJECT_STRING, "somePath", new URI("http://server/dist/gradle.zip"));
        assertEquals(file(currentDirPath() + "/somePath/gradle.zip"), dist);
    }

    @Test(expected = RuntimeException.class)
    public void distZipWithUnknownBase() throws Exception {
        pathAssembler.distZip("unknownBase", "somePath", new URI("http://server/dist/gradle.zip"));
    }

    private File file(String path) {
        return new File(path);
    }
    
    private String currentDirPath() {
        return System.getProperty("user.dir");
    }
}
