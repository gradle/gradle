/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.integtests;

import org.apache.commons.io.FileUtils;
import org.gradle.CacheUsage;
import org.gradle.StartParameter;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;

public class AbstractIntegrationTest {
    private File testDir;
    private File defaultImportFile;

    @Before
    public void setupTestDir() throws IOException {
        testDir = HelperUtil.makeNewTestDir();
        defaultImportFile = new File(testDir, "default-imports");
        FileUtils.writeStringToFile(defaultImportFile, "import org.gradle.api.*");
    }

    protected File getTestBuildFile(String name) {
        System.out.println("name = " + name);
        URL resource = getClass().getResource("testProjects/" + name);
        assertThat(resource, notNullValue());
        assertThat(resource.getProtocol(), equalTo("file"));
        File sourceFile;
        try {
            sourceFile = new File(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Could not locate test build file '%s'.", name));
        }

        File destFile = new File(testDir, sourceFile.getName()).getAbsoluteFile();
        try {
            FileUtils.copyFile(sourceFile, destFile);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not copy test build file '%s' to '%s'", sourceFile, destFile), e);
        }
        return destFile;
    }

    protected StartParameter startParameter(File gradleFile, String... tasks) {
        StartParameter parameter = startParameter(tasks);
        parameter.setCurrentDir(gradleFile.getParentFile());
        parameter.setBuildFileName(gradleFile.getName());


        return parameter;
    }

    protected StartParameter startParameter(String... tasks) {
        StartParameter parameter = new StartParameter();

        // TODO: should not have to set these
        parameter.setPluginPropertiesFile(new File(testDir, "plugin.properties"));
        parameter.setGradleUserHomeDir(new File(testDir, "user-home"));

        parameter.setDefaultImportsFile(defaultImportFile);
        parameter.setSearchUpwards(false);
        parameter.setCacheUsage(CacheUsage.ON);
        parameter.setCurrentDir(testDir);

        parameter.setTaskNames(Arrays.asList(tasks));
        return parameter;
    }
}
