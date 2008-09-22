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

import junit.framework.AssertionFailedError;
import org.apache.commons.io.FileUtils;
import org.gradle.Gradle;
import org.gradle.CacheUsage;
import org.gradle.StartParameter;
import org.gradle.BuildResult;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Formatter;

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
            throw new RuntimeException(String.format("Could not copy test build file '%s' to '%s'", sourceFile, destFile), e);
        }
        return destFile;
    }

    protected StartParameter startParameter() {
        StartParameter parameter = new StartParameter();
        parameter.setGradleHomeDir(testFile("gradle-home").asFile());

        TestFile defaultImportFile = testFile("gradle-home/gradle-imports");
        defaultImportFile.write("import org.gradle.api.*\nimport static org.junit.Assert.*\nimport static org.hamcrest.Matchers.*");

        parameter.setGradleUserHomeDir(testFile("user-home").asFile());

        parameter.setSearchUpwards(false);
        parameter.setCacheUsage(CacheUsage.ON);
        parameter.setCurrentDir(getTestDir());

        return parameter;
    }

    protected GradleExecution inDirectory(File file) {
        StartParameter parameter = startParameter();
        parameter.setCurrentDir(file);
        return new GradleExecution(parameter);
    }

    protected GradleExecution usingBuildFile(TestFile file) {
        return usingBuildFile(file.asFile());
    }
    
    protected GradleExecution usingBuildFile(File file) {
        StartParameter parameter = startParameter();
        parameter.setCurrentDir(file.getParentFile());
        parameter.setBuildFileName(file.getName());
        return new GradleExecution(parameter);
    }

    protected GradleExecution usingBuildScript(String script) {
        StartParameter parameter = startParameter();
        parameter.useEmbeddedBuildFile(script);
        return new GradleExecution(parameter);
    }

    public static class TestFile {
        private final File file;

        public TestFile(File file) {
            this.file = file.getAbsoluteFile();
        }

        public TestFile writelns(String... lines) {
            Formatter formatter = new Formatter();
            for (String line : lines) {
                formatter.format("%s%n", line);
            }
            return write(formatter);
        }
        
        public TestFile write(Object content) {
            try {
                FileUtils.writeStringToFile(file, content.toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public File asFile() {
            return file;
        }

        @Override
        public String toString() {
            return file.getPath();
        }
    }
    
    public static class GradleExecution {
        protected final StartParameter parameter;

        public GradleExecution(StartParameter parameter) {
            this.parameter = parameter;
        }

        public GradleExecution withSearchUpwards() {
            parameter.setSearchUpwards(true);
            return this;
        }

        public GradleExecution runTasks(String... names) {
            parameter.setTaskNames(Arrays.asList(names));
            BuildResult result = Gradle.newInstance(parameter).run();
            result.rethrowFailure();
            return this;
        }

        public GradleExecutionFailure runTasksAndExpectFailure(String... names) {
            try {
                runTasks(names);
                throw new AssertionFailedError("expected build to fail.");
            } catch (GradleException e) {
                return new GradleExecutionFailure(e);
            }
        }

        public GradleExecution usingSettingsFile(TestFile settingsFile) {
            return usingSettingsFile(settingsFile.asFile());
        }

        public GradleExecution usingSettingsFile(File settingsFile) {
            assertThat(settingsFile.getParentFile(), equalTo(parameter.getCurrentDir()));
            parameter.setSettingsFileName(settingsFile.getName());
            return this;
        }

        public GradleExecution usingBuildScript(String script) {
            parameter.useEmbeddedBuildFile(script);
            return this;
        }
    }

    public static class GradleExecutionFailure {
        private final GradleException failure;

        public GradleExecutionFailure(GradleException failure) {
            this.failure = failure;
        }

        public void assertHasLineNumber(int lineNumber) {
            assertThat(failure.getMessage(), containsString(String.format(" line(s): %d", lineNumber)));
        }

        public void assertHasFileName(String filename) {
            assertThat(failure.getMessage(), startsWith(String.format("%s", filename)));
        }

        public void assertHasDescription(String description) {
            assertThat(failure.getCause().getMessage(), endsWith(description));
        }

        public void assertHasContext(String context) {
            assertThat(failure.getMessage(), containsString(context));
        }
    }

}
