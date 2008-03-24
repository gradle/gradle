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

package org.gradle.initialization

import groovy.mock.interceptor.MockFor
import org.gradle.Build
import org.gradle.api.internal.project.BuildScriptFinder
import org.gradle.api.internal.project.EmbeddedBuildScriptFinder

/**
 * @author Hans Dockter
 */
class EmbeddedBuildExecuterTest extends GroovyTestCase {
    EmbeddedBuildExecuter embeddedBuildExecuter
    MockFor buildMocker
    Closure mockBuildFactory
    Build build
    int factoryCallCount
    File gradleUserHome
    List expectedTaskNames
    File buildResolverDir
    File projectDir
    String buildScriptName
    Map expectedSystemProperties
    Map expectedProjectProperties
    BuildScriptFinder calledBuildScriptFinder
    File calledBuildResolverDir

    void setUp() {
        build = [:] as Build
        factoryCallCount = 0
        mockBuildFactory = {BuildScriptFinder buildScriptFinder, File buildResolverDir ->
            calledBuildResolverDir = buildResolverDir
            calledBuildScriptFinder = buildScriptFinder
            factoryCallCount++
            build
        }
        gradleUserHome = new File('gradleUserHome')
        embeddedBuildExecuter = new EmbeddedBuildExecuter(mockBuildFactory, gradleUserHome)
        buildMocker = new MockFor(Build)
        expectedTaskNames = ['clean', 'compile']
        projectDir = new File('projectDir')
        buildResolverDir = new File('buildResolverDir')
        buildScriptName = 'buildScriptName'
        expectedSystemProperties = [prop1: 'prop1']
        expectedProjectProperties = [projectProp1: 'projectProp1']
    }

    void testExecute() {
        boolean expectedRecursive = false
        boolean expectedSearchUpwards = false

        buildMocker.demand.run(2..2) {List taskNames, File currentDir, boolean recursive, boolean searchUpwards,
                                      Map projectProperties, Map systemProperties ->
            assertEquals(expectedTaskNames, taskNames)
            assertEquals(projectDir, currentDir)
            assertEquals(this.gradleUserHome, gradleUserHome)
            assertEquals(expectedRecursive, recursive)
            assertEquals(expectedSearchUpwards, searchUpwards)
            assertEquals(expectedProjectProperties, projectProperties)
            assertEquals(expectedSystemProperties, systemProperties)
        }
        buildMocker.use(build) {
            embeddedBuildExecuter.execute(buildResolverDir, projectDir, buildScriptName, expectedTaskNames, expectedProjectProperties,
                    expectedSystemProperties, expectedRecursive, expectedSearchUpwards)
            embeddedBuildExecuter.execute(buildResolverDir, projectDir, buildScriptName, expectedTaskNames, expectedProjectProperties,
                    expectedSystemProperties, expectedRecursive, expectedSearchUpwards)
        }
        assertEquals(2, factoryCallCount)
        assertEquals(calledBuildResolverDir, buildResolverDir)
        assert (calledBuildScriptFinder instanceof BuildScriptFinder) && !(calledBuildScriptFinder instanceof EmbeddedBuildScriptFinder)
    }

    void testExecuteEmbedded() {
        String embeddedScript = 'embeddedScript'
        buildMocker.demand.run(2..2) {List taskNames, File currentDir, Map projectProperties, Map systemProperties ->
            assertEquals(expectedTaskNames, taskNames)
            assertEquals(projectDir, currentDir)
            assertEquals(this.gradleUserHome, gradleUserHome)
            assertEquals(expectedProjectProperties, projectProperties)
            assertEquals(expectedSystemProperties, systemProperties)
        }
        buildMocker.use(build) {
            embeddedBuildExecuter.executeEmbeddedScript(buildResolverDir, projectDir, embeddedScript, expectedTaskNames, expectedProjectProperties,
                    expectedSystemProperties)
            embeddedBuildExecuter.executeEmbeddedScript(buildResolverDir, projectDir, embeddedScript, expectedTaskNames, expectedProjectProperties,
                    expectedSystemProperties)
        }
        assertEquals(2, factoryCallCount)
        assertEquals(calledBuildResolverDir, buildResolverDir)
        assert calledBuildScriptFinder instanceof EmbeddedBuildScriptFinder
    }
}
