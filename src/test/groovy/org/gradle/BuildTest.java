/*
 * Copyright 2002-2007 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.initialization.DefaultSettings;
import org.gradle.initialization.ProjectsLoader;
import org.gradle.initialization.RootFinder;
import org.gradle.initialization.SettingsProcessor;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class  BuildTest {
    private ProjectsLoader projectsLoaderMock;
    private RootFinder rootFinderMock;
    private SettingsProcessor settingsProcessorMock;
    private BuildConfigurer buildConfigurerMock;
    private File expectedCurrentDir;
    private File expectedGradleUserHomeDir;
    private DefaultProject expectedRootProject;
    private DefaultProject expectedCurrentProject;
    private URLClassLoader expectedClassLoader;
    private DefaultSettings settingsMock;
    private boolean expectedSearchUpwards;
    private Map expectedProjectProperties;
    private Map expectedSystemPropertiesArgs;
    private List<String> expectedTaskNames;
    private List<Iterable<Task>> expectedTasks;
    private StartParameter expectedStartParams;

    private Map testGradleProperties = new HashMap();

    private Build build;

    private BuildExecuter buildExecuterMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        HelperUtil.deleteTestDir();
        rootFinderMock = context.mock(RootFinder.class);
        settingsMock = context.mock(DefaultSettings.class);
        buildExecuterMock = context.mock(BuildExecuter.class);
        settingsProcessorMock = context.mock(SettingsProcessor.class);
        projectsLoaderMock = context.mock(ProjectsLoader.class);
        buildConfigurerMock = context.mock(BuildConfigurer.class);
        build = new Build(rootFinderMock, settingsProcessorMock, projectsLoaderMock,
                buildConfigurerMock, buildExecuterMock);

        testGradleProperties = WrapUtil.toMap(Project.SYSTEM_PROP_PREFIX + ".prop1", "value1");
        testGradleProperties.put("prop2", "value2");
        expectedSearchUpwards = false;
        expectedClassLoader = new URLClassLoader(new URL[0]);
        expectedProjectProperties = WrapUtil.toMap("prop", "value");
        expectedSystemPropertiesArgs = WrapUtil.toMap("systemProp", "systemPropValue");

        expectedCurrentDir = new File("currentDir");
        expectedGradleUserHomeDir = new File(HelperUtil.TMP_DIR_FOR_TEST, "gradleUserHomeDir");

        expectedRootProject = HelperUtil.createRootProject(new File("dir1"));
        expectedCurrentProject = HelperUtil.createRootProject(new File("dir2"));

        expectTasks("a", "b");
        
        expectedStartParams = new StartParameter();
        expectedStartParams.setTaskNames(expectedTaskNames);
        expectedStartParams.setCurrentDir(expectedCurrentDir);
        expectedStartParams.setSearchUpwards(expectedSearchUpwards);
        expectedStartParams.setGradleUserHomeDir(expectedGradleUserHomeDir);
        expectedStartParams.setSystemPropertiesArgs(expectedSystemPropertiesArgs);
        expectedStartParams.setProjectProperties(expectedProjectProperties);

        context.checking(new Expectations() {
            {
                allowing(rootFinderMock).find(with(any(StartParameter.class)));
                allowing(rootFinderMock).getGradleProperties();
                will(returnValue(testGradleProperties));
                allowing(settingsMock).createClassLoader();
                will(returnValue(expectedClassLoader));
                allowing(projectsLoaderMock).getRootProject();
                will(returnValue(expectedRootProject));
                allowing(projectsLoaderMock).getCurrentProject();
                will(returnValue(expectedCurrentProject));
            }
        });
    }

    private void expectTasks(String... tasks) {
        expectedTaskNames = WrapUtil.toList(tasks);
        expectedTasks = new ArrayList<Iterable<Task>>();
        for (String task : tasks) {
            expectedTasks.add(WrapUtil.toSortedSet(expectedCurrentProject.createTask(task)));
        }
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void testRun() {
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).process(rootFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
            }
        });
        setRunExpectations(true);
        build.run(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testRunWithRecreateDagFalse() {
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).process(rootFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
            }
        });
        setRunExpectations(false);
        build.run(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testRunWithDefaultTasks() {
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).process(rootFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
            }
        });
        expectedStartParams.setTaskNames(new ArrayList<String>());
        expectedTaskNames = WrapUtil.toList("c", "d");
        expectedCurrentProject.setDefaultTasks(expectedTaskNames);
        expectTasks("c", "d");
        setRunExpectations(false);
        build.run(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testRunWithEmbeddedScript() {
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).createBasicSettings(rootFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
            }
        });
        setRunExpectations(true);
        build.runNonRecursivelyWithCurrentDirAsRoot(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    private void setRunExpectations(final boolean rebuildDagReturnValue) {
        context.checking(new Expectations() {
            {
                one(buildConfigurerMock).process(expectedRootProject);
                one(buildExecuterMock).execute(expectedTasks.get(0), expectedRootProject, true);
                will(returnValue(rebuildDagReturnValue));
                one(buildExecuterMock).execute(expectedTasks.get(1), expectedRootProject, false);
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        expectedProjectProperties, System.getProperties(), System.getenv());
                if (rebuildDagReturnValue) {
                    one(buildConfigurerMock).process(expectedRootProject);
                    one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams, expectedProjectProperties, System.getProperties(), System.getenv());
                }
            }
        });
    }


    @Test(expected = UnknownTaskException.class)
    public void testRunWithUnknownTask() {
        expectedStartParams.setTaskNames(WrapUtil.toList("unknown"));
        context.checking(new Expectations() {
            {
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        expectedProjectProperties, System.getProperties(), System.getenv());
                one(settingsProcessorMock).process(rootFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
                one(buildConfigurerMock).process(expectedRootProject);
            }
        });
        build.run(expectedStartParams);
    }

    @Test
    public void testTaskList() {
        setTaskExpectations();
        context.checking(new Expectations() {
            {
                one(settingsProcessorMock).process(rootFinderMock, expectedStartParams);
                will(returnValue(settingsMock));
               one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParams,
                        expectedProjectProperties, System.getProperties(), System.getenv());
            }
        });
        build.taskList(expectedStartParams);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    @Test
    public void testTaskListEmbedded() {
        final StartParameter expectedStartParameterArg = StartParameter.newInstance(expectedStartParams);
        expectedStartParameterArg.setSearchUpwards(false);
        context.checking(new Expectations() {
            {
                one(projectsLoaderMock).load(settingsMock, expectedClassLoader, expectedStartParameterArg,
                        expectedProjectProperties, System.getProperties(), System.getenv());
                one(settingsProcessorMock).createBasicSettings(rootFinderMock, expectedStartParameterArg);
                will(returnValue(settingsMock));
            }
        });
        setTaskExpectations();
        build.taskListNonRecursivelyWithCurrentDirAsRoot(expectedStartParameterArg);
        checkSystemProps(expectedSystemPropertiesArgs);
    }

    private void setTaskExpectations() {
        context.checking(new Expectations() {
            {
                one(buildConfigurerMock).taskList(expectedRootProject, true, expectedCurrentProject);
            }
        });
    }

    private void checkSystemProps(Map props) {
        assertFalse(System.getProperties().keySet().contains("prop2"));
        assertEquals(testGradleProperties.get(Project.SYSTEM_PROP_PREFIX + ".prop1"), System.getProperty("prop1"));
    }

    // todo: This test is rather weak. Make it stronger.
    //@Test
    public void testNewInstanceFactory() {
        File expectedPluginProps = new File("pluginProps");
        File expectedDefaultImports = new File("defaultImports");

        StartParameter startParameter = new StartParameter();
        startParameter.setBuildFileName("buildfile");
        startParameter.setDefaultImportsFile(new File("imports"));
        startParameter.setPluginPropertiesFile(new File("plugin"));
        Build build = Build.newInstanceFactory(startParameter).newInstance(
                "embeddedscript",
                new File("buildResolverDir"));
//        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
//        assertEquals(expectedDefaultImports, build.settingsProcessor.importsReader.defaultImportsFile)
        build = Build.newInstanceFactory(startParameter).newInstance(null, null);
//        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
    }

}
