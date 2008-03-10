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
    MockFor buildScriptsProcessorMocker
    File expectedCurrentDir
    File expectedGradleUserHomeDir
    DefaultProject expectedProjectLoaderRoot
    DefaultProject expectedProjectLoaderCurrent
    String expectedBuildFileName
    URLClassLoader expectedClassLoader
    boolean expectedRecursive
    DefaultSettings expectedSettings
    boolean expectedSearchUpwards

    Build build

    void setUp() {
        settingsProcessorMocker = new MockFor(SettingsProcessor)
        projectLoaderMocker = new StubFor(ProjectsLoader)
        buildScriptsProcessorMocker = new MockFor(BuildConfigurer)
        expectedRecursive = false
        expectedSearchUpwards = false
        expectedBuildFileName = "buildFileName"
        expectedClassLoader = new URLClassLoader([] as URL[])

        expectedCurrentDir = new File('currentDir')
        expectedGradleUserHomeDir = new File('gradleUserHomeDir')
        expectedSettings = [:] as DefaultSettings
        settingsProcessorMocker.demand.process(1..1) {File currentDir, boolean searchUpwards ->
            assertSame(expectedCurrentDir, currentDir)
            assertEquals(expectedSearchUpwards, searchUpwards)
            expectedSettings
        }

        expectedProjectLoaderRoot = [:] as DefaultProject
        expectedProjectLoaderCurrent = [:] as DefaultProject
        projectLoaderMocker.demand.load(1..1) {DefaultSettings settings, File gradleUserHomeDir ->
            assertSame(expectedSettings, settings)
            assert gradleUserHomeDir.is(expectedGradleUserHomeDir)
        }
        projectLoaderMocker.demand.getRootProject(2..2) {expectedProjectLoaderRoot}
        projectLoaderMocker.demand.getCurrentProject() {expectedProjectLoaderCurrent}
    }

    void testBuild() {
        List expectedTaskNames = ['a', 'b']

        MockFor buildExecuterMocker = new MockFor(BuildExecuter)
        buildScriptsProcessorMocker.demand.process(1..1) {DefaultProject root, URLClassLoader urlClassLoader ->
            assert urlClassLoader.is(expectedClassLoader)
            assertSame(expectedProjectLoaderRoot, root)
        }
        buildExecuterMocker.demand.execute(1..1) {List taskNames, boolean recursive, DefaultProject projectLoaderCurrent, DefaultProject projectLoaderRoot ->
            assertEquals(expectedTaskNames, taskNames)
            assertEquals(expectedRecursive, recursive)
            assertSame(expectedProjectLoaderCurrent, projectLoaderCurrent)
            assertSame(expectedProjectLoaderRoot, projectLoaderRoot)
        }
        MockFor settingsMocker = new MockFor(DefaultSettings)
        settingsMocker.demand.createClassLoader(1..1) {
            expectedClassLoader }

        settingsMocker.use(expectedSettings) {
            settingsProcessorMocker.use {
                projectLoaderMocker.use {
                    buildScriptsProcessorMocker.use {
                        buildExecuterMocker.use {
                            new Build(new SettingsProcessor(), new ProjectsLoader(), new BuildConfigurer(), new BuildExecuter()).run(
                                    expectedTaskNames, expectedCurrentDir, expectedGradleUserHomeDir, expectedBuildFileName, expectedRecursive, expectedSearchUpwards)
                        }
                    }
                }
            }
        }
        projectLoaderMocker.expect.verify()
    }

    void testTargetList() {
        buildScriptsProcessorMocker.demand.taskList(1..1) {DefaultProject root, boolean recursive, DefaultProject current, URLClassLoader urlClassLoader ->
            assert urlClassLoader.is(expectedClassLoader)
            assertSame(expectedProjectLoaderRoot, root)
            assertSame(expectedProjectLoaderCurrent, current)
            assertEquals(expectedRecursive, recursive)
        }
        MockFor settingsMocker = new MockFor(DefaultSettings)
        settingsMocker.demand.createClassLoader(1..1) { expectedClassLoader }

        settingsMocker.use(expectedSettings) {
            settingsProcessorMocker.use {
                projectLoaderMocker.use {
                    buildScriptsProcessorMocker.use {
                        new Build(new SettingsProcessor(), new ProjectsLoader(), new BuildConfigurer(), new BuildExecuter()).taskList(
                                expectedCurrentDir, expectedGradleUserHomeDir, expectedBuildFileName, expectedRecursive, expectedSearchUpwards)
                    }
                }
            }
        }
    }

}