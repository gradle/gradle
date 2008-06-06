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
import org.gradle.util.HelperUtil
import org.gradle.api.Project
import org.gradle.initialization.RootFinder
import org.gradle.initialization.RootFinder

/**
 * @author Hans Dockter
 */
class BuildTest extends GroovyTestCase {
    StubFor projectLoaderMocker
    RootFinder dummyRootFinder
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
    Map expectedProjectProperties
    Map expectedSystemPropertiesArgs
    List expectedTaskNames

    Map userHomeGradleProperties = [:]
    Map rootProjectGradleProperties = [:]

    Closure testBuildFactory

    void setUp() {
        HelperUtil.deleteTestDir()
        dummyRootFinder = [find: { File currentDir, boolean searchUpwards -> expectedSettings }] as RootFinder
        expectedTaskNames = ['a', 'b']
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        projectLoaderMocker = new StubFor(ProjectsLoader)
        buildConfigurerMocker = new MockFor(BuildConfigurer)
        expectedRecursive = false
        expectedSearchUpwards = false
        expectedClassLoader = new URLClassLoader([] as URL[])
        expectedProjectProperties = [prop: 'value']
        expectedSystemPropertiesArgs = [systemProp: 'systemPropValue']

        expectedCurrentDir = new File('currentDir')
        expectedGradleUserHomeDir = new File(HelperUtil.TMP_DIR_FOR_TEST, 'gradleUserHomeDir')
        expectedSettings = [:] as DefaultSettings
        settingsProcessorMocker.demand.process(1..1) {RootFinder rootFinder ->
            assert rootFinder.is(dummyRootFinder)
            expectedSettings
        }
        expectedRootProject = [:] as DefaultProject
        expectedCurrentProject = [:] as DefaultProject
        projectLoaderMocker.demand.load(1..1) {DefaultSettings settings, File gradleUserHomeDir, Map projectProperties,
                                               Map systemProperties, Map envProperties ->
            assertEquals(expectedProjectProperties, projectProperties)
            assertEquals(System.properties, systemProperties)
            assertEquals(System.getenv(), envProperties)
            assertSame(expectedSettings, settings)
            assert gradleUserHomeDir.is(expectedGradleUserHomeDir)
        }
        projectLoaderMocker.demand.getRootProject(0..10) {expectedRootProject}
        projectLoaderMocker.demand.getCurrentProject(0..10) {expectedCurrentProject}
        testBuildFactory = {
            new Build(expectedGradleUserHomeDir, dummyRootFinder, new SettingsProcessor(), new ProjectsLoader(),
                    new BuildConfigurer(), new BuildExecuter())
        }
        createGradlePropertyFiles()
        dummyRootFinder.rootDir = expectedSettings.rootDir
        dummyRootFinder.currentDir = expectedCurrentDir
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    private void createGradlePropertyFiles() {
        expectedGradleUserHomeDir.mkdirs()
        expectedSettings.rootDir = new File(HelperUtil.TMP_DIR_FOR_TEST, 'root')
        expectedSettings.rootDir.mkdirs()
        userHomeGradleProperties = [(Project.SYSTEM_PROP_PREFIX + '.prop1'): 'value1', (Project.SYSTEM_PROP_PREFIX + '.prop2'): 'value2']
        rootProjectGradleProperties = [(Project.SYSTEM_PROP_PREFIX + '.prop1'): 'value2', (Project.SYSTEM_PROP_PREFIX + '.prop3'): 'value3']
        Properties properties = new Properties()
        properties.putAll(userHomeGradleProperties)
        properties.store(new FileOutputStream(new File(expectedGradleUserHomeDir, Project.GRADLE_PROPERTIES)), '')
        properties.putAll(rootProjectGradleProperties)
        properties.store(new FileOutputStream(new File(expectedSettings.rootDir, Project.GRADLE_PROPERTIES)), '')
    }

    void testRun() {
        checkRun {
            testBuildFactory().run(
                    expectedTaskNames, expectedCurrentDir,
                    expectedRecursive, expectedSearchUpwards, expectedProjectProperties, expectedSystemPropertiesArgs)
        }
    }

    void testRunWithEmbeddedScript() {
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        settingsProcessorMocker.demand.createBasicSettings(1..1) {RootFinder rootFinder ->
            assert rootFinder.is(dummyRootFinder)
            expectedSettings
        }
        checkRun {
            testBuildFactory().run(
                    expectedTaskNames, expectedCurrentDir, expectedProjectProperties, expectedSystemPropertiesArgs)
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
        projectLoaderMocker.demand.load(1..1) {DefaultSettings settings, File gradleUserHomeDir, Map projectProperties,
                                               Map systemProperties, Map envProperties ->
            assertEquals(expectedProjectProperties, projectProperties)
            assertEquals(System.properties, systemProperties)
            assertEquals(System.getenv(), envProperties)
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
            expectedClassLoader
        }

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
        checkSystemProps(expectedSystemPropertiesArgs)
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
            expectedClassLoader
        }

        settingsMocker.use(expectedSettings) {
            settingsProcessorMocker.use {
                projectLoaderMocker.use {
                    buildConfigurerMocker.use {
                        buildExecuterMocker.use {
                            shouldFail(UnknownTaskException) {
                                testBuildFactory().run(
                                        expectedTaskNames, expectedCurrentDir, expectedRecursive,
                                        expectedSearchUpwards, expectedProjectProperties, expectedSystemPropertiesArgs)
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
                    expectedProjectProperties, expectedSystemPropertiesArgs)
        }
    }

    void testTaskListEmbedded() {
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        settingsProcessorMocker.demand.createBasicSettings(1..1) {RootFinder rootFinder ->
            assert rootFinder.is(dummyRootFinder)
            expectedSettings
        }
        checkTask {
            testBuildFactory().taskList(
                    expectedCurrentDir, expectedProjectProperties, expectedSystemPropertiesArgs)
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
        checkSystemProps(expectedSystemPropertiesArgs)
    }

    private checkSystemProps(Map props) {
        Map expectedProps = new HashMap(props)
        expectedProps.putAll(rootProjectGradleProperties)
        expectedProps.putAll(userHomeGradleProperties)
        expectedProps.each {String key, value ->
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                key = key.substring((Project.SYSTEM_PROP_PREFIX + '.').length())
            }
            assertEquals(value, System.properties[key])
        }
    }

    // todo: This test is rather weak. Make it stronger.
    void testNewInstanceFactory() {
        File expectedPluginProps = new File('pluginProps')
        File expectedDefaultImports = new File('defaultImports')
        Build build = Build.newInstanceFactory(expectedGradleUserHomeDir, expectedPluginProps, expectedDefaultImports).call(
                new BuildScriptFinder(),
                new File('buildResolverDir'))
        assertEquals(expectedGradleUserHomeDir, build.gradleUserHomeDir)
        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
        assertEquals(expectedDefaultImports, build.settingsProcessor.importsReader.defaultImportsFile)
        build = Build.newInstanceFactory(expectedGradleUserHomeDir, expectedPluginProps, expectedDefaultImports).call(new BuildScriptFinder(),
                null)
        assertEquals(expectedGradleUserHomeDir, build.gradleUserHomeDir)
        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
    }

}