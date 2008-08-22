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
import org.gradle.Build;
import org.gradle.CacheUsage;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.util.HelperUtil;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
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

    protected StartParameter startParameter() {
        StartParameter parameter = new StartParameter();

        // TODO: should not have to set these
        parameter.setPluginPropertiesFile(new File(testDir, "plugin.properties"));
        parameter.setGradleUserHomeDir(new File(testDir, "user-home"));

        parameter.setDefaultImportsFile(defaultImportFile);
        parameter.setSearchUpwards(false);
        parameter.setCacheUsage(CacheUsage.ON);
        parameter.setCurrentDir(testDir);

        return parameter;
    }

    public static class GradleExecution {
        protected final StartParameter parameter;
        private final String script;

        public GradleExecution(StartParameter parameter, String script) {
            this.parameter = parameter;
            this.script = script;
        }

        public GradleExecution runTasks(String... names) {
            parameter.setTaskNames(Arrays.asList(names));
            if (script == null) {
                Build.newInstanceFactory(parameter).newInstance(null, null).run(parameter);
            } else {
                Build.newInstanceFactory(parameter).newInstance(script, null).runNonRecursivelyWithCurrentDirAsRoot(parameter);
            }
            return this;
        }

        public GradleExecutionFailure runTasksAndExpectFailure(String... names) {
            try {
                runTasks(names);
                fail();
            } catch (GradleException e) {
                return new GradleExecutionFailure(e);
            }
            throw new AssertionFailedError();
        }
    }

    public static class GradleExecutionFailure {
        private final GradleException failure;

        public GradleExecutionFailure(GradleException failure) {
            this.failure = failure;
        }

        public void assertHasLineNumber(int lineNumber) {
            assertThat(failure.getMessage(), Matchers.containsString(String.format("line(s): %d", lineNumber)));
        }
    }

    protected GradleExecution usingBuildFile(File file) {
        StartParameter parameter = startParameter();
        parameter.setCurrentDir(file.getParentFile());
        parameter.setBuildFileName(file.getName());
        return new GradleExecution(parameter, null);
    }

    protected GradleExecution usingBuildScript(String script) {
        StartParameter parameter = startParameter();
        parameter.setBuildFileName(Project.EMBEDDED_SCRIPT_ID);
        return new GradleExecution(parameter, script);
    }
}
