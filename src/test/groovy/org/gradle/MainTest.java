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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 *         todo write disabled test 'testMainWithException' as integration test
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class MainTest {
    // This property has to be also set as system property gradle.home when running this test
    private final static String TEST_GRADLE_HOME = "roadToNowhere";

    private String expectedBuildFileName;
    private String expectedSettingsFileName;
    private File expectedGradleUserHome;
    private File expectedGradleImportsFile;
    private File expectedPluginPropertiesFile;
    private File expectedProjectDir;
    private List expectedTaskNames = WrapUtil.toList("clean", "compile");
    private Map expectedSystemProperties;
    private Map expectedProjectProperties;
    private CacheUsage expectedCacheUsage;
    private boolean expectedSearchUpwards;
    private String expectedEmbeddedScript;
    private StartParameter actualStartParameter;

    private Build buildMock;
    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() throws IOException {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        buildMock = context.mock(Build.class);

        Build.injectCustomFactory(new Build.BuildFactory() {
            public Build newInstance(StartParameter startParameter) {
                actualStartParameter = startParameter;
                return buildMock;
            }
        });

        expectedGradleUserHome = new File(Main.DEFAULT_GRADLE_USER_HOME);
        expectedGradleImportsFile = new File(TEST_GRADLE_HOME, Main.IMPORTS_FILE_NAME);
        expectedPluginPropertiesFile = new File(TEST_GRADLE_HOME, Main.DEFAULT_PLUGIN_PROPERTIES);
        expectedTaskNames = WrapUtil.toList("clean", "compile");
        expectedProjectDir = new File("").getCanonicalFile();
        expectedProjectProperties = new HashMap();
        expectedSystemProperties = new HashMap();
        expectedSettingsFileName = Settings.DEFAULT_SETTINGS_FILE;
        expectedBuildFileName = Project.DEFAULT_BUILD_FILE;
        expectedCacheUsage = CacheUsage.ON;
        expectedSearchUpwards = true;
        expectedEmbeddedScript = "somescript";
    }

    @After
    public void tearDown() {
        Build.injectCustomFactory(null);
    }

//    @Test public void testMainWithSpecifiedNonExistingProjectDirectory() {
//        fileStub.demand.getCanonicalFile {new File(TEST_DIR_NAME)}
//        fileStub.demand.isDirectory {false}
//        buildMockFor.use {
//            fileStub.use {
//                Main.main(args(["-p", TEST_DIR_NAME]) as String[])
//            }
//        }
//        // The buildMockFor throws an exception, if the main method does not return prematurely (what it should do).
//    }

    private static interface MainCall {
        void execute() throws Throwable;
    }

    private class StartParameterMatcher extends BaseMatcher<StartParameter> {
        boolean emptyTasks;

        public StartParameterMatcher(boolean emptyTasks) {
            this.emptyTasks = emptyTasks;
        }

        public boolean matches(Object o) {
            StartParameter parameter = (StartParameter) o;
            checkStartParameter(parameter, emptyTasks);
            return true;
        }

        public void describeTo(Description description) {
            description.appendText("Check StartParameter");
        }


    }

    @Test
    public void testMainWithoutAnyOptions() throws Throwable {
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-S"));
            }
        });
    }

    private void checkMain(MainCall mainCall) throws Throwable {
        checkMain(false, false, mainCall);
    }

    private void checkStartParameter(StartParameter startParameter, boolean emptyTasks) {
        assertEquals(expectedBuildFileName, startParameter.getBuildFileName());
        assertEquals(expectedSettingsFileName, startParameter.getSettingsFileName());
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
    }

    private void checkMain(final boolean embedded, final boolean noTasks, MainCall mainCall) throws Throwable {
        final BuildResult testBuildResult = new BuildResult(context.mock(Settings.class), null);
        context.checking(new Expectations() {
            {
                one(buildMock).addBuildListener(with(notNullValue(BuildExceptionReporter.class)));
                one(buildMock).addBuildListener(with(notNullValue(BuildResultLogger.class)));
                if (noTasks) {
                    one(buildMock).run(with(new StartParameterMatcher(true))); will(returnValue(testBuildResult));
                } else {
                    one(buildMock).run(with(new StartParameterMatcher(false))); will(returnValue(testBuildResult));
                }
            }
        });

        mainCall.execute();
        // We check the params passed to the build factory
        checkStartParameter(actualStartParameter, noTasks);
        assertNull(actualStartParameter.getBuildResolverDirectory());
        if (embedded) {
            assertThat(actualStartParameter.getBuildScriptSource().getText(), equalTo(expectedEmbeddedScript));
        } else {
            assert !GUtil.isTrue(actualStartParameter.getBuildScriptSource());
        }
    }

    @Test
    public void testMainWithSpecifiedGradleUserHomeDirectory() throws Throwable {
        expectedGradleUserHome = HelperUtil.makeNewTestDir();
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-S", "-g", expectedGradleUserHome.getAbsoluteFile().toString()));
            }
        });
    }

    @Test
    public void testMainWithSpecifiedExistingProjectDirectory() throws Throwable {
        expectedProjectDir = HelperUtil.makeNewTestDir();
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-S", "-p", expectedProjectDir.getAbsoluteFile().toString()));
            }
        });
    }

    @Test
    public void testMainWithDisabledDefaultImports() throws Throwable {
        expectedGradleImportsFile = null;
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-SI"));
            }
        });
    }

    @Test
    public void testMainWithSpecifiedBuildFileName() throws Throwable {
        expectedBuildFileName = "somename";
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-S", "-b", expectedBuildFileName));
            }
        });
    }

    @Test
    public void testMainWithSpecifiedSettingsFileName() throws Throwable {
        expectedSettingsFileName = "somesettings";
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-S", "-c", expectedSettingsFileName));
            }
        });
    }

    @Test
    public void testMainWithSystemProperties() throws Throwable {
        final String prop1 = "gradle.prop1";
        final String valueProp1 = "value1";
        final String prop2 = "gradle.prop2";
        final String valueProp2 = "value2";
        expectedSystemProperties = WrapUtil.toMap(prop1, valueProp1);
        expectedSystemProperties.put(prop2, valueProp2);
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-D", prop1 + "=" + valueProp1, "-S", "-D", prop2 + "=" + valueProp2));
            }
        });
    }

    @Test
    public void testMainWithStartProperties() throws Throwable {
        final String prop1 = "prop1";
        final String valueProp1 = "value1";
        final String prop2 = "prop2";
        final String valueProp2 = "value2";
        expectedProjectProperties = WrapUtil.toMap(prop1, valueProp1);
        expectedProjectProperties.put(prop2, valueProp2);
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-P", prop1 + "=" + valueProp1, "-S", "-P", prop2 + "=" + valueProp2));
            }
        });
    }

    @Test
    public void testMainWithCacheOffFlagSet() throws Throwable {
        expectedCacheUsage = CacheUsage.OFF;
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-Sx"));
            }
        });
    }

    @Test
    public void testMainWithNoTasks() throws Throwable {
        expectedTaskNames = new ArrayList();
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-S"));
            }
        });
    }

    @Test
    public void testMainWithRebuildCacheFlagSet() throws Throwable {
        expectedCacheUsage = CacheUsage.REBUILD;
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-Sr"));
            }
        });
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithMutuallyExclusiveCacheFlags() throws Throwable {
        Main.main(new String[]{"-S", "-xr", "clean"});
    }

    @Test
    public void testMainWithSearchUpwardsFlagSet() throws Throwable {
        expectedSearchUpwards = false;
        checkMain(new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-Su"));
            }
        });
    }

    @Test
    public void testMainWithEmbeddedScript() throws Throwable {
        expectedBuildFileName = Project.EMBEDDED_SCRIPT_ID;
        expectedSearchUpwards = false;
        checkMain(true, false, new MainCall() {
            public void execute() throws Throwable {
                Main.main(args("-S", "-e", expectedEmbeddedScript));
            }
        });
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithEmbeddedScriptAndConflictingNoSearchUpwardsOption() throws Throwable {
        Main.main(new String[]{"-S", "-e", "someScript", "-u", "clean"});
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithEmbeddedScriptAndConflictingSpecifyBuildFileOption() throws Throwable {
        Main.main(new String[]{"-S", "-e", "someScript", "-bsomeFile", "clean"});
    }

    @Test(expected = InvalidUserDataException.class)
    public void testMainWithEmbeddedScriptAndConflictingSpecifySettingsFileOption() throws Throwable {
        Main.main(new String[]{"-S", "-e", "someScript", "-csomeFile", "clean"});
    }

    @Test
    public void testMainWithShowTasks() throws Throwable {
        checkMain(false, true, new MainCall() {
            public void execute() throws Throwable {
                Main.main(new String[]{"-St"});
            }
        });
    }

    @Test
    public void testMainWithShowTasksAndEmbeddedScript() throws Throwable {
        expectedBuildFileName = Project.EMBEDDED_SCRIPT_ID;
        expectedSearchUpwards = false;
        checkMain(true, true, new MainCall() {
            public void execute() throws Throwable {
                Main.main(new String[]{"-S", "-e", expectedEmbeddedScript, "-t"});
            }
        });
    }

    @Test
    public void testMainWithPParameterWithoutArgument() throws Throwable {
        Main.main(new String[]{"-S", "-p"});

        // The projectLoaderMock throws an exception, if the main method does not return prematurely (what it should do).
    }

    @Test
    public void testMainWithMissingGradleHome() throws Throwable {
        System.getProperties().remove(Main.GRADLE_HOME_PROPERTY_KEY);
        try {
            Main.main(new String[]{"-S", "clean"});
            fail();
        } catch (InvalidUserDataException e) {
            // ignore
        }
        // Tests are run in one JVM. Therefore we need to set it again.
        System.getProperties().put(Main.GRADLE_HOME_PROPERTY_KEY, TEST_GRADLE_HOME);
    }

    private String[] args(String... prefix) {
        List<String> allArgs = new ArrayList<String>(expectedTaskNames);
        allArgs.addAll(0, Arrays.asList(prefix));
        return allArgs.toArray(new String[allArgs.size()]);
    }

    //    @Test void testMainWithException() {
    //        showProp()
    //        buildMockFor.demand.run {List taskNames, File currentDir, String buildFileName, boolean recursive, boolean searchUpwards ->
    //            throw new RuntimeException()
    //        }
    //        buildMockFor.use {
    //            Main.main(["clean", "compile"] as String[])
    //            // Getting here means the exception was caught. This is what we want to test.
    //        }
    //    }


}

