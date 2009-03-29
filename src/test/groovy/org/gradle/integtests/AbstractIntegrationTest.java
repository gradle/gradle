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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class AbstractIntegrationTest {
    private File testDir;

    @Before
    public void setupTestDir() throws IOException {
        testDir = HelperUtil.makeNewTestDir().getCanonicalFile();
    }

    public File getTestDir() {
        return testDir;
    }

    public TestFile testFile(String name) {
        return new TestFile(new File(getTestDir(), name));
    }

    public TestFile testFile(File dir, String name) {
        return new TestFile(new File(dir, name));
    }

    protected File getTestBuildFile(String name) {
        URL resource = getClass().getResource("testProjects/" + name);
        assertThat(resource, notNullValue());
        assertThat(resource.getProtocol(), equalTo("file"));
        File sourceFile;
        try {
            sourceFile = new File(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Could not locate test build file '%s'.", name));
        }

        File destFile = testFile(sourceFile.getName()).asFile();
        try {
            FileUtils.copyFile(sourceFile, destFile);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not copy test build file '%s' to '%s'", sourceFile,
                    destFile), e);
        }
        return destFile;
    }

    private StartParameter startParameter() {
        StartParameter parameter = new StartParameter();
        parameter.setGradleHomeDir(testFile("gradle-home").asFile());

        testFile("gradle-home/gradle-imports").writelns("import org.gradle.api.*", "import static org.junit.Assert.*",
                "import static org.hamcrest.Matchers.*");

        testFile("gradle-home/plugin.properties").writelns(
                "java=org.gradle.api.plugins.JavaPlugin",
                "groovy=org.gradle.api.plugins.GroovyPlugin"
        );

        parameter.setGradleUserHomeDir(testFile("user-home").asFile());

        parameter.setSearchUpwards(false);
        parameter.setCacheUsage(CacheUsage.ON);
        parameter.setCurrentDir(getTestDir());

        return parameter;
    }

    protected GradleExecuter inTestDirectory() {
        return inDirectory(testDir);
    }

    protected GradleExecuter inDirectory(File directory) {
        StartParameter parameter = startParameter();
        return new InProcessGradleExecuter(parameter).inDirectory(directory);
    }

    protected GradleExecuter usingBuildFile(TestFile file) {
        return usingBuildFile(file.asFile());
    }

    protected GradleExecuter usingBuildFile(File file) {
        StartParameter parameter = startParameter();
        parameter.setBuildFile(file);
        return new InProcessGradleExecuter(parameter);
    }

    protected GradleExecuter usingBuildScript(String script) {
        StartParameter parameter = startParameter();
        parameter.useEmbeddedBuildFile(script);
        return new InProcessGradleExecuter(parameter);
    }

    protected GradleExecuter usingProjectDir(TestFile projectDir) {
        return usingProjectDir(projectDir.asFile());
    }

    protected GradleExecuter usingProjectDir(File projectDir) {
        StartParameter parameter = startParameter();
        parameter.setProjectDir(projectDir);
        return new InProcessGradleExecuter(parameter);
    }
}
