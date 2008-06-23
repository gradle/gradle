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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.configuration.BuildConfigurer
import org.gradle.execution.BuildExecuter
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.ProjectsLoader
import org.gradle.initialization.SettingsProcessor
import org.gradle.util.HelperUtil
import org.gradle.api.Project
import org.gradle.initialization.RootFinder
import org.gradle.api.internal.project.BuildScriptProcessor

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
    StartParameter expectedStartParams

    Map testGradleProperties = [:]

    Closure testBuildFactory

    void setUp() {
        HelperUtil.deleteTestDir()
        testGradleProperties = [(Project.SYSTEM_PROP_PREFIX + '.prop1'): 'value1', prop2: 'value2']
        dummyRootFinder = [
                find: {StartParameter startParameter -> expectedSettings },
                getGradleProperties: {-> testGradleProperties }
        ] as RootFinder
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
        expectedSettings = [createClassLoader: {
            expectedClassLoader
        }] as DefaultSettings

        expectedStartParams = new StartParameter(
                taskNames: expectedTaskNames,
                currentDir: expectedCurrentDir,
                recursive: expectedRecursive,
                searchUpwards: expectedSearchUpwards,
                projectProperties: expectedProjectProperties,
                systemPropertiesArgs: expectedSystemPropertiesArgs,
                gradleUserHomeDir: expectedGradleUserHomeDir
        )
        settingsProcessorMocker.demand.process(1..1) {RootFinder rootFinder, StartParameter startParameter ->
            assert rootFinder.is(dummyRootFinder)
            assert startParameter.is(expectedStartParams)
            expectedSettings
        }
        expectedRootProject = [:] as DefaultProject
        expectedCurrentProject = [:] as DefaultProject
        projectLoaderMocker.demand.load(1..1) {DefaultSettings settings, ClassLoader classLoader, StartParameter startParameter, Map projectProperties,
                                               Map systemProperties, Map envProperties ->
            assertEquals(expectedProjectProperties, projectProperties)
            assertEquals(System.properties, systemProperties)
            assertEquals(System.getenv(), envProperties)
            assert settings.is(expectedSettings)
            assert classLoader.is(expectedClassLoader)
            assertEquals(expectedStartParams, startParameter)
        }
        projectLoaderMocker.demand.getRootProject(0..10) {expectedRootProject}
        projectLoaderMocker.demand.getCurrentProject(0..10) {expectedCurrentProject}
        testBuildFactory = {
            new Build(dummyRootFinder, new SettingsProcessor(), new ProjectsLoader(),
                    new BuildConfigurer(), new BuildExecuter())
        }
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testRun() {
        checkRun {
            testBuildFactory().run(expectedStartParams)
        }
    }

    void testRunWithEmbeddedScript() {
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        settingsProcessorMocker.demand.createBasicSettings(1..1) {RootFinder rootFinder, StartParameter startParameter ->
            assert rootFinder.is(dummyRootFinder)
            assert startParameter.is(expectedStartParams)
            expectedSettings
        }
        checkRun {
            testBuildFactory().runNonRecursivelyWithCurrentDirAsRoot(expectedStartParams)
        }
    }

    private void checkRun(Closure runMethodCall) {
        MockFor buildExecuterMocker = new MockFor(BuildExecuter)
        buildConfigurerMocker.demand.process(1..1) {DefaultProject root ->
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
        projectLoaderMocker.demand.load(1..1) {DefaultSettings settings, ClassLoader classLoader, StartParameter startParameter, Map projectProperties,
                                               Map systemProperties, Map envProperties ->
            assertEquals(expectedProjectProperties, projectProperties)
            assertEquals(System.properties, systemProperties)
            assertEquals(System.getenv(), envProperties)
            assertSame(expectedSettings, settings)
            assert classLoader.is(expectedClassLoader)
        assert startParameter.is(expectedStartParams)
        }
        buildConfigurerMocker.demand.process(1..1) {DefaultProject root ->
            assertSame(expectedRootProject, root)
        }
        buildExecuterMocker.demand.execute(1..1) {String taskName, boolean recursive, DefaultProject projectLoaderCurrent, DefaultProject projectLoaderRoot ->
            assertEquals(expectedTaskNames[1], taskName)
            assertEquals(expectedRecursive, recursive)
            assertSame(expectedCurrentProject, projectLoaderCurrent)
            assertSame(expectedRootProject, projectLoaderRoot)
        }
        settingsProcessorMocker.use {
            projectLoaderMocker.use {
                buildConfigurerMocker.use {
                    buildExecuterMocker.use {
                        runMethodCall.call()
                    }
                }
            }
        }
        checkSystemProps(expectedSystemPropertiesArgs)
        projectLoaderMocker.expect.verify()
    }

    void testRunWithUnknownTask() {
        List expectedTaskNames = ['a', 'b']
        buildConfigurerMocker.demand.process(1..1) {DefaultProject root ->
            assertSame(expectedRootProject, root)
        }
        MockFor buildExecuterMocker = new MockFor(BuildExecuter)
        buildExecuterMocker.demand.unknownTasks(1..1) {List taskNames, boolean recursive, DefaultProject currentProject ->
            assertEquals(taskNames, expectedTaskNames)
            assertEquals(expectedRecursive, recursive)
            assert currentProject.is(expectedCurrentProject)
            ['a']
        }
        settingsProcessorMocker.use {
            projectLoaderMocker.use {
                buildConfigurerMocker.use {
                    buildExecuterMocker.use {
                        shouldFail(UnknownTaskException) {
                            testBuildFactory().run(expectedStartParams)
                        }
                    }
                }
            }
        }
    }

    void testTaskList() {
        checkTask {
            testBuildFactory().taskList(expectedStartParams)
        }
    }

    void testTaskListEmbedded() {
        StartParameter expectedStartParameterArg = StartParameter.newInstance(expectedStartParams, searchUpwards: false)
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        settingsProcessorMocker.demand.createBasicSettings(1..1) {RootFinder rootFinder, StartParameter startParameter ->
            assert rootFinder.is(dummyRootFinder)
            assertEquals(expectedStartParameterArg, startParameter)
            expectedSettings
        }
        expectedStartParams = expectedStartParameterArg
        checkTask {
            testBuildFactory().taskListNonRecursivelyWithCurrentDirAsRoot(expectedStartParams)
        }
    }

    private void checkTask(Closure taskListCall) {
        buildConfigurerMocker.demand.taskList(1..1) {DefaultProject root, boolean recursive, DefaultProject current ->
            assertSame(expectedRootProject, root)
            assertSame(expectedCurrentProject, current)
            assertEquals(expectedRecursive, recursive)
        }
        settingsProcessorMocker.use {
            projectLoaderMocker.use {
                buildConfigurerMocker.use {
                    taskListCall.call()
                }
            }
        }
        checkSystemProps(expectedSystemPropertiesArgs)
    }

    private checkSystemProps(Map props) {
        assertEquals(testGradleProperties[Project.SYSTEM_PROP_PREFIX + '.prop1'], System.properties['prop1'])
        assertFalse(System.properties.keySet().contains('prop2'))
    }

    // todo: This test is rather weak. Make it stronger.
    void testNewInstanceFactory() {
        File expectedPluginProps = new File('pluginProps')
        File expectedDefaultImports = new File('defaultImports')
        StartParameter startParameter = new StartParameter(
                buildFileName: 'buildfile',
                defaultImportsFile: new File('imports'),
                pluginPropertiesFile: new File('plugin')
        )
        Build build = Build.newInstanceFactory(startParameter).call(
                'embeddedscript',
                new File('buildResolverDir'))
//        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
//        assertEquals(expectedDefaultImports, build.settingsProcessor.importsReader.defaultImportsFile)
        build = Build.newInstanceFactory(startParameter).call(null, null)
//        assertEquals(expectedDefaultImports, build.projectLoader.buildScriptProcessor.importsReader.defaultImportsFile)
    }

}