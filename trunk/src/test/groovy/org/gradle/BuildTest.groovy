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

package org.gradle

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.project.BuildScriptFinder
import org.gradle.api.internal.project.DefaultProject
import org.gradle.configuration.BuildConfigurer
import org.gradle.execution.BuildExecuter
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.ProjectsLoader
import org.gradle.initialization.SettingsProcessor

/**
 * @author Hans Dockter
 */
class BuildTest extends GroovyTestCase {
    StubFor projectLoaderMocker
    MockFor settingsProcessorMocker
    MockFor buildConfigurerMocker
    File expectedCurrentDir
    File expectedGradleUserHomeDir
    DefaultProject expectedRootProject
    DefaultProject expectedCurrentProject
    URLClassLoader expectedClassLoader
    boolean expectedRecursive
    DefaultSettings expectedSettings
    boolean expectedSearchUpwards
    Map expectedStartProperties
    Map expectedSystemProperties
    List expectedTaskNames

    Closure testBuildFactory

    Build build

    void setUp() {
        expectedTaskNames = ['a', 'b']
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        projectLoaderMocker = new StubFor(ProjectsLoader)
        buildConfigurerMocker = new MockFor(BuildConfigurer)
        expectedRecursive = false
        expectedSearchUpwards = false
        expectedClassLoader = new URLClassLoader([] as URL[])
        expectedStartProperties = [prop: 'value']
        expectedSystemProperties = [systemProp: 'systemPropValue']

        expectedCurrentDir = new File('currentDir')
        expectedGradleUserHomeDir = new File('gradleUserHomeDir')
        expectedSettings = [:] as DefaultSettings
        settingsProcessorMocker.demand.process(1..1) {File currentDir, boolean searchUpwards ->
            assertSame(expectedCurrentDir, currentDir)
            assertEquals(expectedSearchUpwards, searchUpwards)
            expectedSettings
        }
        expectedRootProject = [:] as DefaultProject
        expectedCurrentProject = [:] as DefaultProject
        projectLoaderMocker.demand.load(1..1) {DefaultSettings settings, File gradleUserHomeDir, Map startProperties ->
            assertEquals(expectedStartProperties, startProperties)
            assertSame(expectedSettings, settings)
            assert gradleUserHomeDir.is(expectedGradleUserHomeDir)
        }
        projectLoaderMocker.demand.getRootProject(0..10) {expectedRootProject}
        projectLoaderMocker.demand.getCurrentProject(0..10) {expectedCurrentProject}
        testBuildFactory = {new Build(expectedGradleUserHomeDir, new SettingsProcessor(), new ProjectsLoader(),
                new BuildConfigurer(), new BuildExecuter())}
    }

    void testRun() {
        checkRun {
            testBuildFactory().run(
                    expectedTaskNames, expectedCurrentDir,
                    expectedRecursive, expectedSearchUpwards, expectedStartProperties, expectedSystemProperties)
        }
    }

    void testRunWithEmbeddedScript() {
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        settingsProcessorMocker.demand.createBasicSettings(1..1) {File currentDir ->
            assertSame(expectedCurrentDir, currentDir)
            expectedSettings
        }
        checkRun {
            testBuildFactory().run(
                    expectedTaskNames, expectedCurrentDir, expectedStartProperties, expectedSystemProperties)
        }
    }

    private void checkRun(Closure runMethodCall) {
        MockFor buildExecuterMocker = new MockFor(BuildExecuter)
        buildConfigurerMocker.demand.process(1..1) {DefaultProject root, URLClassLoader urlClassLoader ->
            assert urlClassLoader.is(expectedClassLoader)
            assertSame(expectedRootProject, root)
        }
        buildExecuterMocker.demand.unknownTasks(1..1) {List taskNames, boolean recursive, DefaultProject currentProject ->
            assertEquals(taskNames, expectedTaskNames)
            assertEquals(expectedRecursive, recursive)
            assert currentProject.is(expectedCurrentProject)
            []
        }
        buildExecuterMocker.demand.execute(1..1) {String taskName, boolean recursive, DefaultProject projectLoaderCurrent, DefaultProject projectLoaderRoot ->
            assertEquals(expectedTaskNames[0], taskName)
            assertEquals(expectedRecursive, recursive)
            assertSame(expectedCurrentProject, projectLoaderCurrent)
            assertSame(expectedRootProject, projectLoaderRoot)
        }
        projectLoaderMocker.demand.load(1..1) {DefaultSettings settings, File gradleUserHomeDir, Map startProperties ->
            assertEquals(expectedStartProperties, startProperties)
            assertSame(expectedSettings, settings)
            assert gradleUserHomeDir.is(expectedGradleUserHomeDir)
        }
        buildConfigurerMocker.demand.process(1..1) {DefaultProject root, URLClassLoader urlClassLoader ->
            assert urlClassLoader.is(expectedClassLoader)
            assertSame(expectedRootProject, root)
        }
        buildExecuterMocker.demand.execute(1..1) {String taskName, boolean recursive, DefaultProject projectLoaderCurrent, DefaultProject projectLoaderRoot ->
            assertEquals(expectedTaskNames[1], taskName)
            assertEquals(expectedRecursive, recursive)
            assertSame(expectedCurrentProject, projectLoaderCurrent)
            assertSame(expectedRootProject, projectLoaderRoot)
        }
        MockFor settingsMocker = new MockFor(DefaultSettings)
        settingsMocker.demand.createClassLoader(1..1) {
            expectedClassLoader}

        settingsMocker.use(expectedSettings) {
            settingsProcessorMocker.use {
                projectLoaderMocker.use {
                    buildConfigurerMocker.use {
                        buildExecuterMocker.use {
                            runMethodCall.call()
                        }
                    }
                }
            }
        }
        checkSystemProps(expectedSystemProperties)
        projectLoaderMocker.expect.verify()
    }

    void testRunWithUnknownTask() {
        List expectedTaskNames = ['a', 'b']
        buildConfigurerMocker.demand.process(1..1) {DefaultProject root, URLClassLoader urlClassLoader ->
            assert urlClassLoader.is(expectedClassLoader)
            assertSame(expectedRootProject, root)
        }
        MockFor buildExecuterMocker = new MockFor(BuildExecuter)
        buildExecuterMocker.demand.unknownTasks(1..1) {List taskNames, boolean recursive, DefaultProject currentProject ->
            assertEquals(taskNames, expectedTaskNames)
            assertEquals(expectedRecursive, recursive)
            assert currentProject.is(expectedCurrentProject)
            ['a']
        }
        MockFor settingsMocker = new MockFor(DefaultSettings)
        settingsMocker.demand.createClassLoader(1..1) {
            expectedClassLoader}

        settingsMocker.use(expectedSettings) {
            settingsProcessorMocker.use {
                projectLoaderMocker.use {
                    buildConfigurerMocker.use {
                        buildExecuterMocker.use {
                            shouldFail(UnknownTaskException) {
                                testBuildFactory().run(
                                        expectedTaskNames, expectedCurrentDir, expectedRecursive,
                                        expectedSearchUpwards, expectedStartProperties, expectedSystemProperties)
                            }
                        }
                    }
                }
            }
        }
    }

    void testTaskList() {
        checkTask {
            testBuildFactory().taskList(
                    expectedCurrentDir, expectedRecursive, expectedSearchUpwards,
                    expectedStartProperties, expectedSystemProperties)
        }
    }

    void testTaskListEmbedded() {
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        settingsProcessorMocker.demand.createBasicSettings(1..1) {File currentDir ->
            assertSame(expectedCurrentDir, currentDir)
            expectedSettings
        }
        checkTask {
            testBuildFactory().taskList(
                    expectedCurrentDir, expectedStartProperties, expectedSystemProperties)
        }
    }

    private void checkTask(Closure taskListCall) {
        buildConfigurerMocker.demand.taskList(1..1) {DefaultProject root, boolean recursive, DefaultProject current, URLClassLoader urlClassLoader ->
            assert urlClassLoader.is(expectedClassLoader)
            assertSame(expectedRootProject, root)
            assertSame(expectedCurrentProject, current)
            assertEquals(expectedRecursive, recursive)
        }
        MockFor settingsMocker = new MockFor(DefaultSettings)
        settingsMocker.demand.createClassLoader(1..1) {expectedClassLoader}

        settingsMocker.use(expectedSettings) {
            settingsProcessorMocker.use {
                projectLoaderMocker.use {
                    buildConfigurerMocker.use {
                        taskListCall.call()
                    }
                }
            }
        }
        checkSystemProps(expectedSystemProperties)
    }

    private checkSystemProps(Map props) {
        props.each {key, value ->
            assertEquals(value, System.properties[key])
        }
    }

    // todo: This test is rather weak. Make it stronger.
    void testNewInstanceFactory() {
        File expectedPluginProps = new File('pluginProps')
        Build build = Build.newInstanceFactory(expectedGradleUserHomeDir, expectedPluginProps).call(new BuildScriptFinder(),
                new File('buildResolverDir'))
        assertEquals(expectedGradleUserHomeDir, build.gradleUserHomeDir)
        build = Build.newInstanceFactory(expectedGradleUserHomeDir, expectedPluginProps).call(new BuildScriptFinder(),
                null)
        assertEquals(expectedGradleUserHomeDir, build.gradleUserHomeDir)
    }

}