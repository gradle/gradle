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

package org.gradle.initialization;

import org.gradle.CacheUsage;
import org.gradle.RefreshOptions;
import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.logging.ShowStacktrace;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class DefaultCommandLineConverterTest {
    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();

    private TestFile currentDir = testDir.file("current-dir");
    private File expectedBuildFile;
    private File expectedGradleUserHome = StartParameter.DEFAULT_GRADLE_USER_HOME;
    private File expectedProjectDir = currentDir;
    private List<String> expectedTaskNames = toList();
    private Set<String> expectedExcludedTasks = toSet();
    private boolean buildProjectDependencies = true;
    private Map<String, String> expectedSystemProperties = new HashMap<String, String>();
    private Map<String, String> expectedProjectProperties = new HashMap<String, String>();
    private List<File> expectedInitScripts = new ArrayList<File>();
    private CacheUsage expectedCacheUsage = CacheUsage.ON;
    private boolean expectedSearchUpwards = true;
    private boolean expectedDryRun;
    private ShowStacktrace expectedShowStackTrace = ShowStacktrace.INTERNAL_EXCEPTIONS;
    private LogLevel expectedLogLevel = LogLevel.LIFECYCLE;
    private boolean expectedColorOutput = true;
    private StartParameter actualStartParameter;
    private boolean expectedProfile;
    private File expectedProjectCacheDir;
    private boolean expectedRefreshDependencies;
    private boolean expectedRerunTasks;
    private final DefaultCommandLineConverter commandLineConverter = new DefaultCommandLineConverter();
    private boolean expectedContinue;
    private boolean expectedOffline;
    private RefreshOptions expectedRefreshOptions = RefreshOptions.NONE;
    private boolean expectedRecompileScripts;
    private int expectedParallelExecutorCount;
    private boolean expectedConfigureOnDemand;

    @Test
    public void withoutAnyOptions() {
        checkConversion();
    }

    private void checkConversion(String... args) {
        actualStartParameter = new StartParameter();
        actualStartParameter.setCurrentDir(currentDir);
        commandLineConverter.convert(asList(args), actualStartParameter);
        // We check the params passed to the build factory
        checkStartParameter(actualStartParameter);
    }

    private void checkStartParameter(StartParameter startParameter) {
        assertEquals(expectedBuildFile, startParameter.getBuildFile());
        assertEquals(expectedTaskNames, startParameter.getTaskNames());
        assertEquals(buildProjectDependencies, startParameter.isBuildProjectDependencies());
        assertEquals(expectedProjectDir.getAbsoluteFile(), startParameter.getCurrentDir().getAbsoluteFile());
        assertEquals(expectedCacheUsage, startParameter.getCacheUsage());
        assertEquals(expectedSearchUpwards, startParameter.isSearchUpwards());
        assertEquals(expectedProjectProperties, startParameter.getProjectProperties());
        assertEquals(expectedSystemProperties, startParameter.getSystemPropertiesArgs());
        assertEquals(expectedGradleUserHome.getAbsoluteFile(), startParameter.getGradleUserHomeDir().getAbsoluteFile());
        assertEquals(expectedLogLevel, startParameter.getLogLevel());
        assertEquals(expectedColorOutput, startParameter.isColorOutput());
        assertEquals(expectedDryRun, startParameter.isDryRun());
        assertEquals(expectedShowStackTrace, startParameter.getShowStacktrace());
        assertEquals(expectedExcludedTasks, startParameter.getExcludedTaskNames());
        assertEquals(expectedInitScripts, startParameter.getInitScripts());
        assertEquals(expectedProfile, startParameter.isProfile());
        assertEquals(expectedContinue, startParameter.isContinueOnFailure());
        assertEquals(expectedOffline, startParameter.isOffline());
        assertEquals(expectedRecompileScripts, startParameter.isRecompileScripts());
        assertEquals(expectedRerunTasks, startParameter.isRerunTasks());
        assertEquals(expectedRefreshOptions, startParameter.getRefreshOptions());
        assertEquals(expectedRefreshDependencies, startParameter.isRefreshDependencies());
        assertEquals(expectedProjectCacheDir, startParameter.getProjectCacheDir());
        assertEquals(expectedParallelExecutorCount, startParameter.getParallelThreadCount());
        assertEquals(expectedConfigureOnDemand, startParameter.isConfigureOnDemand());
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
        expectedProjectDir = testDir.file("project-dir");
        checkConversion("-p", expectedProjectDir.getAbsolutePath());

        expectedProjectDir = currentDir.file("project-dir");
        checkConversion("-p", "project-dir");
    }

    @Test
    public void withSpecifiedBuildFileName() throws IOException {
        expectedBuildFile = testDir.file("somename");
        expectedProjectDir = expectedBuildFile.getParentFile();
        checkConversion("-b", expectedBuildFile.getAbsolutePath());

        expectedBuildFile = currentDir.file("somename");
        expectedProjectDir = expectedBuildFile.getParentFile();
        checkConversion("-b", "somename");
    }

    @Test
    public void withSpecifiedSettingsFileName() throws IOException {
        File expectedSettingsFile = currentDir.file("somesettings");
        expectedProjectDir = expectedSettingsFile.getParentFile();

        checkConversion("-c", "somesettings");

        assertThat(actualStartParameter.getSettingsFile(), equalTo(expectedSettingsFile));
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
        checkConversion("-D", propName+"="+propValue);
    }

    @Test
    public void privilegeCmdLineOptionOverSystemPrefForGradleUserHome() {
        expectedGradleUserHome = testDir.file("home");
        String propName = "gradle.user.home";
        String propValue = "home2";
        expectedSystemProperties = toMap(propName, propValue);
        checkConversion("-D", propName+"="+propValue, "-g", expectedGradleUserHome.getAbsolutePath());
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

    @Test
    public void withRebuildCacheFlagSet() {
        expectedCacheUsage = CacheUsage.REBUILD;
        checkConversion("-C", "rebuild");
    }

    @Test
    public void withCacheOnFlagSet() {
        checkConversion("-C", "on");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withUnknownCacheFlags() {
        checkConversion("-C", "unknown");
    }

    @Test
    public void withSearchUpwardsFlagSet() {
        expectedSearchUpwards = false;
        checkConversion("-u");
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
    public void withDryRunFlagSet() {
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
    public void withEmbeddedScriptAndConflictingNoSearchUpwardsOption() {
        checkConversion("-e", "someScript", "-u", "clean");
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
    public void withNoColor() {
        expectedColorOutput = false;
        checkConversion("--no-color");
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
        expectedRefreshOptions = new RefreshOptions(asList(RefreshOptions.Option.DEPENDENCIES));
        checkConversion("--refresh-dependencies");
        checkConversion("-refresh-dependencies");
    }

    @Test
    public void withRecompileScripts() {
        expectedRecompileScripts = true;
        checkConversion("--recompile-scripts");
    }

    @Test
    public void withRefreshDependenciesSet() {
        expectedRefreshDependencies = true;
        expectedRefreshOptions = new RefreshOptions(Arrays.asList(RefreshOptions.Option.DEPENDENCIES));
        checkConversion("--refresh", "dependencies");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withUnknownRefreshOption() {
        checkConversion("--refresh", "unknown");
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
        expectedParallelExecutorCount = -1;
        checkConversion("--parallel");
    }

    @Test
    public void withParallelExecutorThreads() {
        expectedParallelExecutorCount = 5;
        checkConversion("--parallel-threads", "5");
    }

    @Test(expected = CommandLineArgumentException.class)
    public void withInvalidParallelExecutorThreads() {
        checkConversion("--parallel-threads", "foo");
    }

    @Test
    public void withConfigureOnDemand() {
        expectedConfigureOnDemand = true;
        checkConversion("--configure-on-demand");
    }
}
