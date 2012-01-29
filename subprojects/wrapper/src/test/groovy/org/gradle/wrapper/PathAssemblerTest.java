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

import java.io.File;
import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Hans Dockter
 */
public class PathAssemblerTest {
    public static final String TEST_GRADLE_USER_HOME = "someUserHome";
    private PathAssembler pathAssembler = new PathAssembler(new File(TEST_GRADLE_USER_HOME));
    final WrapperConfiguration configuration = new WrapperConfiguration();

    @Before
    public void setup() {
        configuration.setDistributionBase(PathAssembler.GRADLE_USER_HOME_STRING);
        configuration.setDistributionPath("somePath");
        configuration.setZipBase(PathAssembler.GRADLE_USER_HOME_STRING);
        configuration.setZipPath("somePath");
    }
    
    @Test
    public void gradleHomeWithGradleUserHomeBase() throws Exception {
        configuration.setDistribution(new URI("http://server/dist/gradle-0.9-bin.zip"));
        
        File gradleHome = pathAssembler.getDistribution(configuration).getGradleHome();
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9"), gradleHome);
    }

    @Test
    public void gradleHomeWithProjectBase() throws Exception {
        configuration.setDistributionBase(PathAssembler.PROJECT_STRING);
        configuration.setDistribution(new URI("http://server/dist/gradle-0.9-bin.zip"));

        File gradleHome = pathAssembler.getDistribution(configuration).getGradleHome();
        assertEquals(file(currentDirPath() + "/somePath/gradle-0.9"), gradleHome);
    }

    @Test
    public void gradleHomeForUriWithNoPath() throws Exception {
        configuration.setDistribution(new URI("http://server/gradle-0.9-bin.zip"));
        
        File gradleHome = pathAssembler.getDistribution(configuration).getGradleHome();
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9"), gradleHome);
    }

    @Test
    public void gradleHomeForSnapshotVersion() throws Exception {
        configuration.setDistribution(new URI("http://server/gradle-0.9-some-branch-2010+1100-bin.zip"));
        
        File gradleHome = pathAssembler.getDistribution(configuration).getGradleHome();
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9-some-branch-2010+1100"), gradleHome);
    }

    @Test
    public void gradleHomeForUrlWithNoClassifier() throws Exception {
        configuration.setDistribution(new URI("http://server/gradle-0.9.zip"));
        
        File gradleHome = pathAssembler.getDistribution(configuration).getGradleHome();
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9"), gradleHome);

        configuration.setDistribution(new URI("http://server/custom-gradle-0.9.zip"));
        
        gradleHome = pathAssembler.getDistribution(configuration).getGradleHome();
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/custom-gradle-0.9"), gradleHome);
    }

    @Test
    public void failsToDetermineGradleHomeWhenUrlDoesNotContainAnyVersionInformation() throws Exception {
        configuration.setDistribution(new URI("http://server/gradle-bin.zip"));
        
        try {
            pathAssembler.getDistribution(configuration);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Cannot determine Gradle version from distribution URL 'http://server/gradle-bin.zip'.", e.getMessage());
        }
    }

    @Test
    public void gradleHomeWithUnknownBase() throws Exception {
        configuration.setDistributionBase("unknownBase");

        try {
            pathAssembler.getDistribution(configuration);
            fail();
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Base: unknownBase is unknown");
        }
    }

    @Test
    public void distZipWithGradleUserHomeBase() throws Exception {
        configuration.setDistribution(new URI("http://server/dist/gradle-1.0.zip"));

        File dist = pathAssembler.getDistribution(configuration).getDistZip();
        assertEquals(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-1.0.zip"), dist);
    }

    @Test
    public void distZipWithProjectBase() throws Exception {
        configuration.setZipBase(PathAssembler.PROJECT_STRING);
        configuration.setDistribution(new URI("http://server/dist/gradle-1.0.zip"));

        File dist = pathAssembler.getDistribution(configuration).getDistZip();
        assertEquals(file(currentDirPath() + "/somePath/gradle-1.0.zip"), dist);
    }

    private File file(String path) {
        return new File(path);
    }
    
    private String currentDirPath() {
        return System.getProperty("user.dir");
    }
}
