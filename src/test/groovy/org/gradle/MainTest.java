/*
 * Copyright 2007 the original author or authors.
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

package org.gradle;

import joptsimple.OptionException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.Settings;
import org.gradle.api.logging.LogLevel;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.BuiltInTasksBuildExecuter;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.Matchers;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 *         todo write disabled test 'testMainWithException' as integration test
 */
@RunWith(JMock.class)
public class MainTest {
    // This property has to be also set as system property gradle.home when running this test
    private final static String TEST_GRADLE_HOME = "roadToNowhere";

    private String previousGradleHome;
    private File expectedBuildFile;
    private File expectedSettingsFile;
    private File expectedGradleUserHome;
    private File expectedGradleImportsFile;
    private File expectedPluginPropertiesFile;
    private File expectedProjectDir;
    private List expectedTaskNames;
    private Map expectedSystemProperties;
    private Map expectedProjectProperties;
    private CacheUsage expectedCacheUsage;
    private boolean expectedSearchUpwards;
    private String expectedEmbeddedScript;
    private StartParameter actualStartParameter;
    private LogLevel expectedLogLevel;

    private Gradle gradleMock;
    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() throws IOException {
        previousGradleHome = System.getProperty("gradle.home");
        System.setProperty("gradle.home", "roadToNowhere");
        context.setImposteriser(ClassImposteriser.INSTANCE);
        gradleMock = context.mock(Gradle.class);

        Gradle.injectCustomFactory(new GradleFactory() {
            public Gradle newInstance(StartParameter startParameter) {
                actualStartParameter = startParameter;
                return gradleMock;
            }
        });

        expectedGradleUserHome = new File(Main.DEFAULT_GRADLE_USER_HOME);
        expectedGradleImportsFile = new File(TEST_GRADLE_HOME, Main.IMPORTS_FILE_NAME).getCanonicalFile();
        expectedPluginPropertiesFile = new File(TEST_GRADLE_HOME, Main.DEFAULT_PLUGIN_PROPERTIES).getCanonicalFile();
        expectedTaskNames = toList();
        expectedProjectDir = new File("").getCanonicalFile();
        expectedProjectProperties = new HashMap();
        expectedSystemProperties = new HashMap();
        expectedSettingsFile = null;
        expectedBuildFile = null;
        expectedCacheUsage = CacheUsage.ON;
        expectedSearchUpwards = true;
        expectedEmbeddedScript = "somescript";
        expectedLogLevel = LogLevel.LIFECYCLE;
    }

    @After
    public void tearDown() {
        if (previousGradleHome != null) {
            System.setProperty("gradle.home", previousGradleHome);
        } else {
            System.getProperties().remove("gradle.home");
        }
        Gradle.injectCustomFactory(null);
    }

    @Test
    public void testMainWithoutAnyOptions() throws Throwable {
        checkMain();
    }

    private void checkMain(String... args) throws Throwable {
        checkMain(false, false, args);
    }

    private void checkStartParameter(StartParameter startParameter, boolean emptyTasks) {
        assertEquals(expectedBuildFile, startParameter.getBuildFile());
        assertEquals(expectedSettingsFile, startParameter.getSettingsFile());
        assertEquals(emptyTasks ? new ArrayList() : expectedTaskNames, startParameter.getTaskNames());
        assertEquals(expectedProjectDir.getAbsoluteFile(), startParameter.getCurrentDir().getAbsoluteFile());
        assertEquals(expectedCacheUsage, startParameter.getCacheUsage());
        assertEquals(expectedSearchUpwards, startParameter.isSearchUpwards());
        assertEquals(expectedProjectProperties, startParameter.getProjectProperties());
        assertEquals(expectedSystemProperties, startParameter.getSystemPropertiesArgs());
        assertEquals(expectedGradleUserHome.getAbsoluteFile(), startParameter.getGradleUserHomeDir().getAbsoluteFile());
        assertEquals(expectedGradleImportsFile, startParameter.getDefaultImportsFile());
        assertEquals(expectedPluginPropertiesFile, startParameter.getPluginPropertiesFile());
        assertEquals(expectedGradleUserHome.getAbsoluteFile(), startParameter.getGradleUserHomeDir().getAbsoluteFile());
        assertEquals(expectedLogLevel, startParameter.getLogLevel());
    }

    private void checkMain(final boolean embedded, final boolean noTasks, String... args) throws Throwable {
        final BuildResult testBuildResult = new BuildResult(context.mock(Settings.class), null);
        context.checking(new Expectations() {
            {
                one(gradleMock).addBuildListener(with(notNullValue(BuildExceptionReporter.class)));
                one(gradleMock).addBuildListener(with(notNullValue(BuildResultLogger.class)));
                if (noTasks) {
                    one(gradleMock).run(); will(returnValue(testBuildResult));
                } else {
                    one(gradleMock).run(); will(returnValue(testBuildResult));
                }
            }
        });

        Main main = new Main(args);
        main.setBuildCompleter(new Main.BuildCompleter() {
            public void exit(Throwable failure) {
                throw new BuildCompletedError(failure);
            }
        });
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat(e.getCause(), nullValue());
        }

        // We check the params passed to the build factory
        checkStartParameter(actualStartParameter, noTasks);
        if (embedded) {
            assertThat(actualStartParameter.getBuildScriptSource().getText(), equalTo(expectedEmbeddedScript));
        } else {
            assert !GUtil.isTrue(actualStartParameter.getBuildScriptSource());
        }
    }

    private void checkMainFails(String... args) throws Throwable {
        Main main = new Main(args);
        main.setBuildCompleter(new Main.BuildCompleter() {
            public void exit(Throwable failure) {
                throw new BuildCompletedError(failure);
            }
        });
        try {
            main.execute();
            fail();
        } catch (BuildCompletedError e) {
            assertThat(e.getCause(), notNullValue());
            throw e.getCause();
        }
    }

    @Test
    public void testMainWithSpecifiedGradleUserHomeDirectory() throws Throwable {
        expectedGradleUserHome = HelperUtil.makeNewTestDir();
        checkMain("-g", expectedGradleUserHome.getAbsoluteFile().toString());
    }

    @Test
    public void testMainWithSpecifiedExistingProjectDirectory() throws Throwable {
        expectedProjectDir = HelperUtil.makeNewTestDir();
        checkMain("-p", expectedProjectDir.getAbsoluteFile().toString());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithSpecifiedNonExistingProjectDirectory() throws Throwable {
        checkMainFails("-p", new File("nonExistingDir").getAbsoluteFile().toString());
    }

    @Test
    public void testMainWithDisabledDefaultImports() throws Throwable {
        expectedGradleImportsFile = null;
        checkMain("-I");
    }

    @Test
    public void testMainWithSpecifiedDefaultImportsFile() throws Throwable {
        expectedGradleImportsFile = new File("somename");
        checkMain("-K", expectedGradleImportsFile.toString());
    }

    @Test
    public void testMainWithSpecifiedPluginPropertiesFile() throws Throwable {
        expectedPluginPropertiesFile = new File("somename");
        checkMain("-l", expectedPluginPropertiesFile.toString());
    }

    @Test
    public void testMainWithSpecifiedBuildFileName() throws Throwable {
        expectedBuildFile = new File("somename").getCanonicalFile();
        checkMain("-b", "somename");
    }

    @Test
    public void testMainWithSpecifiedSettingsFileName() throws Throwable {
        expectedSettingsFile = new File("somesettings").getCanonicalFile();
        checkMain("-c", "somesettings");
    }

    @Test
    public void testMainWithSystemProperties() throws Throwable {
        final String prop1 = "gradle.prop1";
        final String valueProp1 = "value1";
        final String prop2 = "gradle.prop2";
        final String valueProp2 = "value2";
        expectedSystemProperties = toMap(prop1, valueProp1);
        expectedSystemProperties.put(prop2, valueProp2);
        checkMain("-D", prop1 + "=" + valueProp1, "-D", prop2 + "=" + valueProp2);
    }

    @Test
    public void testMainWithStartProperties() throws Throwable {
        final String prop1 = "prop1";
        final String valueProp1 = "value1";
        final String prop2 = "prop2";
        final String valueProp2 = "value2";
        expectedProjectProperties = toMap(prop1, valueProp1);
        expectedProjectProperties.put(prop2, valueProp2);
        checkMain("-P", prop1 + "=" + valueProp1, "-P", prop2 + "=" + valueProp2);
    }

    @Test
    public void testMainWithTaskNames() throws Throwable {
        expectedTaskNames = toList("a", "b");
        checkMain("a", "b");
    }

    @Test
    public void testMainWithCacheOffFlagSet() throws Throwable {
        expectedCacheUsage = CacheUsage.OFF;
        checkMain("-C", "off");
    }

    @Test
    public void testMainWithRebuildCacheFlagSet() throws Throwable {
        expectedCacheUsage = CacheUsage.REBUILD;
        checkMain("-C", "rebuild");
    }

    @Test
    public void testMainWithCacheOnFlagSet() throws Throwable {
        checkMain("-C", "on");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithUnknownCacheFlags() throws Throwable {
        checkMainFails("-C", "unknown");
    }

    @Test
    public void testMainWithSearchUpwardsFlagSet() throws Throwable {
        expectedSearchUpwards = false;
        checkMain("-u");
    }

    @Test
    public void testMainWithEmbeddedScript() throws Throwable {
        expectedSearchUpwards = false;
        checkMain(true, false, "-e", expectedEmbeddedScript);
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithEmbeddedScriptAndConflictingNoSearchUpwardsOption() throws Throwable {
        checkMainFails("-e", "someScript", "-u", "clean");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithEmbeddedScriptAndConflictingSpecifyBuildFileOption() throws Throwable {
        checkMainFails("-e", "someScript", "-bsomeFile", "clean");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithEmbeddedScriptAndConflictingSpecifySettingsFileOption() throws Throwable {
        checkMainFails("-e", "someScript", "-csomeFile", "clean");
    }

    public void testMainWithConflictingLoggingOptionsDQ() throws Throwable {
        List<String> illegalOptions = toList("dq", "di", "qd", "qi", "iq", "id");
        for (String illegalOption : illegalOptions) {
            try {
                checkMainFails("-" + illegalOption, "clean");
            } catch (InvalidUserDataException e) {
                continue;
            }
            fail("Expected InvalidUserDataException");
        }
    }

    @Test
    public void testMainWithQuietLoggingOptions() throws Throwable {
        expectedLogLevel = LogLevel.QUIET;
        checkMain("-q");
    }

    @Test
    public void testMainWithInfoLoggingOptions() throws Throwable {
        expectedLogLevel = LogLevel.INFO;
        checkMain("-i");
    }

    @Test
    public void testMainWithDebugLoggingOptions() throws Throwable {
        expectedLogLevel = LogLevel.DEBUG;
        checkMain("-d");
    }

    @Test
    public void testMainWithShowTasks() throws Throwable {
        checkMain(false, true, "-t");
        BuildExecuter expectedExecuter = new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.TASKS);
        assertThat(actualStartParameter.getBuildExecuter(), Matchers.reflectionEquals(expectedExecuter));
    }

    @Test
    public void testMainWithShowTasksAndEmbeddedScript() throws Throwable {
        expectedSearchUpwards = false;
        checkMain(true, true, "-e", expectedEmbeddedScript, "-t");
    }

    @Test
    public void testMainWithShowProperties() throws Throwable {
        checkMain(false, true, "-r");
        BuildExecuter expectedExecuter = new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.PROPERTIES);
        assertThat(actualStartParameter.getBuildExecuter(), Matchers.reflectionEquals(expectedExecuter));
    }

    @Test
    public void testMainWithShowDependencies() throws Throwable {
        checkMain(false, true, "-n");
        BuildExecuter expectedExecuter = new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.DEPENDENCIES);
        assertThat(actualStartParameter.getBuildExecuter(), Matchers.reflectionEquals(expectedExecuter));
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithShowTasksPropertiesAndDependencies() throws Throwable {
        checkMainFails("-r", "-t");
        checkMainFails("-r", "-n");
        checkMainFails("-r", "-n", "-t");
    }

    @Test
    public void testMainWithPParameterWithoutArgument() throws Throwable {
        try {
            checkMainFails("-p");
            fail();
        } catch (OptionException e) {
            // ignore
        }
    }

    @Test
    public void testMainWithMissingGradleHome() throws Throwable {
        System.getProperties().remove(Main.GRADLE_HOME_PROPERTY_KEY);
        try {
            checkMainFails("clean");
            fail();
        } catch (InvalidUserDataException e) {
            // ignore
        }
    }

    private class BuildCompletedError extends Error {
        public BuildCompletedError(Throwable failure) {
            super(failure);
        }
    }
}

