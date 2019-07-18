/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.gradle.util.WrapUtil.toList;
import static org.gradle.util.WrapUtil.toMap;

public class DefaultCommandLineConverterTest extends CommandLineConverterTestSupport {
    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();

    public DefaultCommandLineConverterTest() {
        super();
        currentDir = testDir.file("current-dir");
        expectedCurrentDir = currentDir;
    }

    @Test
    public void withoutAnyOptions() {
        checkConversion();
    }

    @Test
    public void withSpecifiedGradleUserHomeDirectory() {
        expectedGradleUserHome = testDir.file("home");
        checkConversion("-g", expectedGradleUserHome.getAbsolutePath());

        expectedGradleUserHome = currentDir.file("home");
        checkConversion("-g", "home");
    }

    @Test
    public void withSpecifiedProjectCacheDir() {
        expectedProjectCacheDir = new File(currentDir, ".foo");
        checkConversion("--project-cache-dir", ".foo");
    }

    @Test
    public void withSpecifiedProjectDirectory() {
        expectedCurrentDir = testDir.file("project-dir");
        expectedProjectDir = expectedCurrentDir;
        checkConversion("-p", expectedCurrentDir.getAbsolutePath());

        expectedCurrentDir = currentDir.file("project-dir");
        expectedProjectDir = expectedCurrentDir;
        checkConversion("-p", "project-dir");
    }

    @Test
    public void withSpecifiedBuildFileName() throws IOException {
        expectedBuildFile = testDir.file("somename");
        expectedCurrentDir = expectedBuildFile.getParentFile();
        expectedProjectDir = expectedCurrentDir;
        checkConversion("-b", expectedBuildFile.getAbsolutePath());

        expectedBuildFile = currentDir.file("somename");
        expectedCurrentDir = expectedBuildFile.getParentFile();
        expectedProjectDir = expectedCurrentDir;
        checkConversion("-b", "somename");
    }

    @Test
    public void withSpecifiedSettingsFileName() throws IOException {
        File expectedSettingsFile = currentDir.file("somesettings");
        expectedCurrentDir = expectedSettingsFile.getParentFile();

        checkConversion("-c", "somesettings");

        Assert.assertThat(actualStartParameter.getSettingsFile(), CoreMatchers.equalTo((File) expectedSettingsFile));
    }

    @Test
    public void withInitScripts() {
        File script1 = currentDir.file("init1.gradle");
        expectedInitScripts.add(script1);
        checkConversion("-Iinit1.gradle");

        File script2 = currentDir.file("init2.gradle");
        expectedInitScripts.add(script2);
        checkConversion("-Iinit1.gradle", "-Iinit2.gradle");
    }

    @Test
    public void withSystemProperties() {
        final String prop1 = "gradle.prop1";
        final String valueProp1 = "value1";
        final String prop2 = "gradle.prop2";
        final String valueProp2 = "value2";
        expectedSystemProperties = toMap(prop1, valueProp1);
        expectedSystemProperties.put(prop2, valueProp2);
        checkConversion("-D", prop1 + "=" + valueProp1, "-D", prop2 + "=" + valueProp2);
    }

    @Test
    public void withSpecifiedGradleUserHomeDirectoryBySystemProperty() {
        expectedGradleUserHome = testDir.file("home");
        String propName = "gradle.user.home";
        String propValue = expectedGradleUserHome.getAbsolutePath();
        expectedSystemProperties = toMap(propName, propValue);
        checkConversion("-D", propName + "=" + propValue);
    }

    @Test
    public void privilegeCmdLineOptionOverSystemPrefForGradleUserHome() {
        expectedGradleUserHome = testDir.file("home");
        String propName = "gradle.user.home";
        String propValue = "home2";
        expectedSystemProperties = toMap(propName, propValue);
        checkConversion("-D", propName + "=" + propValue, "-g", expectedGradleUserHome.getAbsolutePath());
    }

    @Test
    public void withStartProperties() {
        final String prop1 = "prop1";
        final String valueProp1 = "value1";
        final String prop2 = "prop2";
        final String valueProp2 = "value2";
        expectedProjectProperties = toMap(prop1, valueProp1);
        expectedProjectProperties.put(prop2, valueProp2);
        checkConversion("-P", prop1 + "=" + valueProp1, "-P", prop2 + "=" + valueProp2);
    }

    @Test
    public void withTaskNames() {
        expectedTaskNames = toList("a", "b");
        checkConversion("a", "b");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withUnknownCacheFlags() {
        checkConversion("-C", "unknown");
    }

    @Test
    public void withShowFullStacktrace() {
        expectedShowStackTrace = ShowStacktrace.ALWAYS_FULL;
        checkConversion("-S");
    }

    @Test
    public void withShowStacktrace() {
        expectedShowStackTrace = ShowStacktrace.ALWAYS;
        checkConversion("-s");
    }

    @Test
    public void withRerunTasks() {
        expectedRerunTasks = true;
        checkConversion("--rerun-tasks");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withShowStacktraceAndShowFullStacktraceShouldThrowCommandLineArgumentEx() {
        checkConversion("-sf");
    }

    @Test
    public void withDryRun() {
        expectedDryRun = true;
        checkConversion("--dry-run");
    }

    @Test
    public void withDryRunShortFlag() {
        expectedDryRun = true;
        checkConversion("-m");
    }

    @Test
    public void withExcludeTask() {
        expectedExcludedTasks.add("excluded");
        checkConversion("-x", "excluded");
        expectedExcludedTasks.add("excluded2");
        checkConversion("-x", "excluded", "-x", "excluded2");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withEmbeddedScriptAndConflictingSpecifyBuildFileOption() {
        checkConversion("-e", "someScript", "-bsomeFile", "clean");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withEmbeddedScriptAndConflictingSpecifySettingsFileOption() {
        checkConversion("-e", "someScript", "-csomeFile", "clean");
    }

    @Test
    public void withNoProjectDependencyRebuild() {
        buildProjectDependencies = false;
        checkConversion("--no-rebuild");
    }

    @Test
    public void withNoProjectDependencyRebuildShortFlag() {
        buildProjectDependencies = false;
        checkConversion("-a");
    }

    @Test
    public void withQuietLoggingOptions() {
        expectedLogLevel = LogLevel.QUIET;
        checkConversion("-q");
    }

    @Test
    public void withInfoLoggingOptions() {
        expectedLogLevel = LogLevel.INFO;
        checkConversion("-i");
    }

    @Test
    public void withDebugLoggingOptions() {
        expectedLogLevel = LogLevel.DEBUG;
        checkConversion("-d");
    }

    @Test
    public void withWarnLoggingOptions() {
        expectedLogLevel = LogLevel.WARN;
        checkConversion("-w");
    }

    @Test
    public void withNoColor() {
        expectedConsoleOutput = ConsoleOutput.Plain;
        checkConversion("--console", "plain");
    }

    @Test
    public void withColor() {
        expectedConsoleOutput = ConsoleOutput.Rich;
        checkConversion("--console", "rich");
    }

    @Test
    public void withColorVerbose() {
        expectedConsoleOutput = ConsoleOutput.Verbose;
        checkConversion("--console", "verbose");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withLowerPParameterWithoutArgument() {
        checkConversion("-p");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withAParameterWithoutArgument() {
        checkConversion("-A");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withUpperAAndLowerAParameter() {
        checkConversion("-a", "-Atask1");
    }

    @Test
    public void withProfile() {
        expectedProfile = true;
        checkConversion("--profile");
    }

    @Test
    public void withContinue() {
        expectedContinue = true;
        checkConversion("--continue");
    }

    @Test
    public void withOffline() {
        expectedOffline = true;
        checkConversion("--offline");
        checkConversion("-offline");
    }

    @Test
    public void withRefreshDependencies() {
        expectedRefreshDependencies = true;
        checkConversion("--refresh-dependencies");
        checkConversion("-refresh-dependencies");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withUnknownOption() {
        checkConversion("--unknown");
    }

    @Test
    public void withTaskAndTaskOption() {
        expectedTaskNames = toList("someTask", "--some-task-option");
        checkConversion("someTask", "--some-task-option");
    }

    @Test
    public void withParallelExecutor() {
        expectedParallelProjectExecution = true;
        checkConversion("--parallel");
    }

    @Test
    public void withMaxWorkers() {
        expectedMaxWorkersCount = 5;
        checkConversion("--max-workers", "5");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withInvalidMaxWorkers() {
        checkConversion("--max-workers", "foo");
    }

    @Test
    public void withConfigureOnDemand() {
        expectedConfigureOnDemand = true;
        checkConversion("--configure-on-demand");
    }

    @Test
    public void withContinuous() {
        expectedContinuous = true;
        checkConversion("--continuous");
    }

    @Test
    public void withContinuousShortFlag() {
        expectedContinuous = true;
        checkConversion("-t");
    }

    @Test
    public void withCompositeBuild() {
        File build1 = currentDir.getParentFile().file("build1");
        expectedParticipants.add(build1);
        checkConversion("--include-build", "../build1");
    }
}
