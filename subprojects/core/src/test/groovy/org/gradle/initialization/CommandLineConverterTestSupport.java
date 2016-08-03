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

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class CommandLineConverterTestSupport {
    protected TestFile currentDir;
    protected File expectedBuildFile;
    protected File expectedGradleUserHome = new BuildLayoutParameters().getGradleUserHomeDir();
    protected File expectedCurrentDir;
    protected File expectedProjectDir;
    protected List<String> expectedTaskNames = WrapUtil.toList();
    protected Set<String> expectedExcludedTasks = WrapUtil.toSet();
    protected boolean buildProjectDependencies = true;
    protected Map<String, String> expectedSystemProperties = new HashMap<String, String>();
    protected Map<String, String> expectedProjectProperties = new HashMap<String, String>();
    protected List<File> expectedInitScripts = new ArrayList<File>();
    protected List<File> expectedParticipants = new ArrayList<File>();
    protected boolean expectedSearchUpwards = true;
    protected boolean expectedDryRun;
    protected ShowStacktrace expectedShowStackTrace = ShowStacktrace.INTERNAL_EXCEPTIONS;
    protected LogLevel expectedLogLevel = LogLevel.LIFECYCLE;
    protected ConsoleOutput expectedConsoleOutput = ConsoleOutput.Auto;
    protected StartParameter actualStartParameter;
    protected boolean expectedProfile;
    protected File expectedProjectCacheDir;
    protected boolean expectedRefreshDependencies;
    protected boolean expectedRerunTasks;
    protected final DefaultCommandLineConverter commandLineConverter = new DefaultCommandLineConverter();
    protected boolean expectedContinue;
    protected boolean expectedOffline;
    protected boolean expectedRecompileScripts;
    protected boolean expectedParallelProjectExecution;
    protected int expectedMaxWorkersCount = Runtime.getRuntime().availableProcessors();
    protected boolean expectedConfigureOnDemand;
    protected boolean expectedContinuous;

    protected void checkConversion(String... args) {
        actualStartParameter = new StartParameter();
        actualStartParameter.setCurrentDir(currentDir);
        commandLineConverter.convert(Arrays.asList(args), actualStartParameter);
        // We check the params passed to the build factory
        checkStartParameter(actualStartParameter);
    }

    protected void checkStartParameter(StartParameter startParameter) {
        assertEquals(expectedBuildFile, startParameter.getBuildFile());
        assertEquals(expectedTaskNames, startParameter.getTaskNames());
        assertEquals(buildProjectDependencies, startParameter.isBuildProjectDependencies());
        if(expectedCurrentDir != null) {
            assertEquals(expectedCurrentDir.getAbsoluteFile(), startParameter.getCurrentDir().getAbsoluteFile());
        }
        assertEquals(expectedProjectDir, startParameter.getProjectDir());
        assertEquals(expectedSearchUpwards, startParameter.isSearchUpwards());
        assertEquals(expectedProjectProperties, startParameter.getProjectProperties());
        assertEquals(expectedSystemProperties, startParameter.getSystemPropertiesArgs());
        assertEquals(expectedGradleUserHome.getAbsoluteFile(), startParameter.getGradleUserHomeDir().getAbsoluteFile());
        assertEquals(expectedLogLevel, startParameter.getLogLevel());
        assertEquals(expectedConsoleOutput, startParameter.getConsoleOutput());
        assertEquals(expectedDryRun, startParameter.isDryRun());
        assertEquals(expectedShowStackTrace, startParameter.getShowStacktrace());
        assertEquals(expectedExcludedTasks, startParameter.getExcludedTaskNames());
        assertEquals(expectedInitScripts, startParameter.getInitScripts());
        assertEquals(expectedProfile, startParameter.isProfile());
        assertEquals(expectedContinue, startParameter.isContinueOnFailure());
        assertEquals(expectedOffline, startParameter.isOffline());
        assertEquals(expectedRecompileScripts, startParameter.isRecompileScripts());
        assertEquals(expectedRerunTasks, startParameter.isRerunTasks());
        assertEquals(expectedRefreshDependencies, startParameter.isRefreshDependencies());
        assertEquals(expectedProjectCacheDir, startParameter.getProjectCacheDir());
        assertEquals(expectedConfigureOnDemand, startParameter.isConfigureOnDemand());
        assertEquals(expectedMaxWorkersCount, startParameter.getMaxWorkerCount());
        assertEquals(expectedContinuous, startParameter.isContinuous());
        assertEquals(expectedParticipants, startParameter.getIncludedBuilds());
    }
}
