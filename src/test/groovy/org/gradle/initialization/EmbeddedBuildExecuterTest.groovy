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
import org.gradle.StartParameter

/**
 * @author Hans Dockter
 */
class EmbeddedBuildExecuterTest extends GroovyTestCase {
    EmbeddedBuildExecuter embeddedBuildExecuter
    MockFor buildMocker
    Closure mockBuildFactory
    Build build
    int factoryCallCount
    StartParameter expectedStartParameter
    File expectedBuildResolverDir
    String expectedEmbeddedScript
    String calledEmbeddedScript
    File calledBuildResolverDir

    void setUp() {
        build = [:] as Build
        factoryCallCount = 0
        mockBuildFactory = {String embeddedScript, File buildResolverDir ->
            calledBuildResolverDir = buildResolverDir
            calledEmbeddedScript = embeddedScript
            factoryCallCount++
            build
        }

        expectedStartParameter = new StartParameter(
                currentDir: new File('projectDir'),
                buildFileName: 'buildScriptName',
                taskNames: ['clean', 'compile'],
                gradleUserHomeDir: new File('gradleUserHome'),
                systemPropertiesArgs: [prop1: 'prop1'],
                projectProperties: [projectProp1: 'projectProp1']
        )
        embeddedBuildExecuter = new EmbeddedBuildExecuter(mockBuildFactory)
        buildMocker = new MockFor(Build)
        expectedBuildResolverDir = new File('buildResolverDir')
        expectedEmbeddedScript = 'somescript'
    }

    void testExecute() {
        StartParameter localExpectedStartParameter = StartParameter.newInstance(expectedStartParameter, recursive: true, searchUpwards: true);
        buildMocker.demand.run(2..2) {StartParameter startParameter ->
            assertEquals(localExpectedStartParameter, startParameter)
        }
        buildMocker.use(build) {
            embeddedBuildExecuter.execute(expectedBuildResolverDir, localExpectedStartParameter)
            embeddedBuildExecuter.execute(expectedBuildResolverDir, localExpectedStartParameter)
        }
        assertEquals(2, factoryCallCount)
        assertEquals(calledBuildResolverDir, expectedBuildResolverDir)
        assertNull(calledEmbeddedScript)
    }

    void testExecuteEmbedded() {
        buildMocker.demand.runNonRecursivelyWithCurrentDirAsRoot(2..2) {StartParameter startParameter ->
            assertEquals(expectedStartParameter, startParameter)
        }
        buildMocker.use(build) {
            embeddedBuildExecuter.executeEmbeddedScript(expectedBuildResolverDir, expectedEmbeddedScript, expectedStartParameter)
            embeddedBuildExecuter.executeEmbeddedScript(expectedBuildResolverDir, expectedEmbeddedScript, expectedStartParameter)
        }
        assertEquals(2, factoryCallCount)
        assertEquals(calledBuildResolverDir, expectedBuildResolverDir)
        assertEquals(expectedEmbeddedScript, calledEmbeddedScript)
    }
}
