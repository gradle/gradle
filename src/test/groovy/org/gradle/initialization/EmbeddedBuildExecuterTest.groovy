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
    File buildResolverDir
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
        buildResolverDir = new File('buildResolverDir')
    }

    void testExecute() {
        StartParameter localExpectedStartParameter = StartParameter.newInstance(expectedStartParameter, recursive: true, searchUpwards: true);
        buildMocker.demand.run(2..2) {StartParameter startParameter ->
            assertEquals(localExpectedStartParameter, startParameter)
        }
        buildMocker.use(build) {
            embeddedBuildExecuter.execute(buildResolverDir, localExpectedStartParameter)
            embeddedBuildExecuter.execute(buildResolverDir, localExpectedStartParameter)
        }
        assertEquals(2, factoryCallCount)
        assertEquals(calledBuildResolverDir, buildResolverDir)
        assert (calledBuildScriptFinder instanceof BuildScriptFinder) && !(calledBuildScriptFinder instanceof EmbeddedBuildScriptFinder)
    }

    void testExecuteEmbedded() {
        String embeddedScript = 'embeddedScript'
        buildMocker.demand.runNonRecursivelyWithCurrentDirAsRoot(2..2) {StartParameter startParameter ->
            assertEquals(expectedStartParameter, startParameter)
        }
        buildMocker.use(build) {
            embeddedBuildExecuter.executeEmbeddedScript(buildResolverDir, embeddedScript, expectedStartParameter)
            embeddedBuildExecuter.executeEmbeddedScript(buildResolverDir, embeddedScript, expectedStartParameter)
        }
        assertEquals(2, factoryCallCount)
        assertEquals(calledBuildResolverDir, buildResolverDir)
        assert calledBuildScriptFinder instanceof EmbeddedBuildScriptFinder
    }
}
