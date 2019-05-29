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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

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
    public void distributionDirWithGradleUserHomeBase() throws Exception {
        configuration.setDistribution(new URI("http://server/dist/gradle-0.9-bin.zip"));
        
        File distributionDir = pathAssembler.getDistribution(configuration).getDistributionDir();
        assertThat(distributionDir.getName(), equalTo("emn8ua2x0re2y4jlewhnxhasz"));
        assertThat(distributionDir.getParentFile(), equalTo(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-0.9-bin")));
    }

    @Test
    public void distributionDirWithProjectBase() throws Exception {
        configuration.setDistributionBase(PathAssembler.PROJECT_STRING);
        configuration.setDistribution(new URI("http://server/dist/gradle-0.9-bin.zip"));

        File distributionDir = pathAssembler.getDistribution(configuration).getDistributionDir();
        assertThat(distributionDir.getName(), equalTo("emn8ua2x0re2y4jlewhnxhasz"));
        assertThat(distributionDir.getParentFile(), equalTo(file(currentDirPath() + "/somePath/gradle-0.9-bin")));
    }

    @Test
    public void distributionDirWithUnknownBase() throws Exception {
        configuration.setDistribution(new URI("http://server/dist/gradle-1.0.zip"));
        configuration.setDistributionBase("unknownBase");

        try {
            pathAssembler.getDistribution(configuration);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Base: unknownBase is unknown", e.getMessage());
        }
    }

    @Test
    public void distZipWithGradleUserHomeBase() throws Exception {
        configuration.setDistribution(new URI("http://server/dist/gradle-1.0.zip"));

        File dist = pathAssembler.getDistribution(configuration).getZipFile();
        assertThat(dist.getName(), equalTo("gradle-1.0.zip"));
        assertThat(dist.getParentFile().getName(), equalTo("98xa9n94mamfu7vl4mzwomw11"));
        assertThat(dist.getParentFile().getParentFile(), equalTo(file(TEST_GRADLE_USER_HOME + "/somePath/gradle-1.0")));
    }

    @Test
    public void distZipWithProjectBase() throws Exception {
        configuration.setZipBase(PathAssembler.PROJECT_STRING);
        configuration.setDistribution(new URI("http://server/dist/gradle-1.0.zip"));

        File dist = pathAssembler.getDistribution(configuration).getZipFile();
        assertThat(dist.getName(), equalTo("gradle-1.0.zip"));
        assertThat(dist.getParentFile().getName(), equalTo("98xa9n94mamfu7vl4mzwomw11"));
        assertThat(dist.getParentFile().getParentFile(), equalTo(file(currentDirPath() + "/somePath/gradle-1.0")));
    }

    private File file(String path) {
        return new File(path);
    }
    
    private String currentDirPath() {
        return System.getProperty("user.dir");
    }
}
