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
import org.gradle.api.Project
import org.gradle.util.HelperUtil
import org.gradle.StartParameter

/**
 * @author Hans Dockter
 */
class BuildSourceBuilderTest extends GroovyTestCase {
    BuildSourceBuilder buildSourceBuilder
    EmbeddedBuildExecuter embeddedBuildExecuter
    MockFor embeddedBuildExecuterMocker
    File rootDir
    File testBuildSrcDir
    File testBuildResolverDir
    StartParameter expectedStartParameter

    void setUp() {
        File testDir = HelperUtil.makeNewTestDir()
        (rootDir = new File(testDir, 'root')).mkdir()
        (testBuildSrcDir = new File(rootDir, 'buildSrc')).mkdir()
        (testBuildResolverDir = new File(testDir, 'testBuildResolverDir')).mkdir()
        embeddedBuildExecuter = new EmbeddedBuildExecuter()
        buildSourceBuilder = new BuildSourceBuilder(embeddedBuildExecuter)
        embeddedBuildExecuterMocker = new MockFor(EmbeddedBuildExecuter)
        expectedStartParameter = new StartParameter(
                searchUpwards: true,
                currentDir: testBuildSrcDir,
                buildFileName: Project.DEFAULT_PROJECT_FILE,
                taskNames: ['task1', 'task2'],
                gradleUserHomeDir: new File('gradleUserHome'),
                projectProperties: dependencyProjectProps
        )
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testBuildSourceBuilder() {
        assert buildSourceBuilder.embeddedBuildExecuter.is(embeddedBuildExecuter)
    }

    void testBuildArtifactFile() {
        String expectedPath = "$testBuildResolverDir.absolutePath/$BuildSourceBuilder.BUILD_SRC_ORG" +
                "/$BuildSourceBuilder.BUILD_SRC_MODULE/$BuildSourceBuilder.BUILD_SRC_REVISION/jars/${BuildSourceBuilder.BUILD_SRC_MODULE}.jar"
        assertEquals(new File(expectedPath), buildSourceBuilder.buildArtifactFile(testBuildResolverDir))
    }

    void testCreateDependencyWithExistingBuildSources() {
        embeddedBuildExecuterMocker.demand.execute(1..1) {File buildResolverDir, StartParameter startParameter ->
            createArtifact()
            assertEquals(testBuildResolverDir, buildResolverDir)
            assertEquals(StartParameter.newInstance(expectedStartParameter, searchUpwards: false), startParameter)
        }
        createBuildFile()
        embeddedBuildExecuterMocker.use(embeddedBuildExecuter) {
            def result = buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter)
            assertEquals(result, BuildSourceBuilder.BUILD_SRC_ID)
        }
    }

    void testCreateDependencyWithNonExistingBuildScript() {
        embeddedBuildExecuterMocker.demand.executeEmbeddedScript(1..1) {File buildResolverDir, String embeddedScript, StartParameter startParameter ->
            createArtifact()
            assertEquals(testBuildResolverDir, buildResolverDir)
            assertEquals(embeddedScript, BuildSourceBuilder.DEFAULT_SCRIPT)
            assertEquals(StartParameter.newInstance(expectedStartParameter, searchUpwards: false), startParameter)
        }
        embeddedBuildExecuterMocker.use(embeddedBuildExecuter) {
            def result = buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter)
            assertEquals(result, BuildSourceBuilder.BUILD_SRC_ID)
        }
    }

    void testCreateDependencyWithNonExistingBuildSrcDir() {
        expectedStartParameter = StartParameter.newInstance(expectedStartParameter, currentDir: new File('nonexisting'))
        assertNull(buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter))
    }

    void testCreateDependencyWithNoArtifactProducingBuild() {
        embeddedBuildExecuterMocker.demand.executeEmbeddedScript(1..1) {File buildResolverDir, String embeddedScript, StartParameter startParameter -> }
        embeddedBuildExecuterMocker.use(embeddedBuildExecuter) {
            assertNull(buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter))
        }
    }

    void testCreateDependencyWithEmptyTaskList() {
        createBuildFile()
        expectedStartParameter = StartParameter.newInstance(expectedStartParameter, taskNames: [])
        assertNull(buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter))
    }

    private createBuildFile() {
        new File(testBuildSrcDir, Project.DEFAULT_PROJECT_FILE).createNewFile()
    }

    private createArtifact() {
        buildSourceBuilder.buildArtifactFile(testBuildResolverDir).parentFile.mkdirs()
        buildSourceBuilder.buildArtifactFile(testBuildResolverDir).createNewFile()
    }

    private Map getDependencyProjectProps() {
        [group: BuildSourceBuilder.BUILD_SRC_ORG,
                version: BuildSourceBuilder.BUILD_SRC_REVISION,
                type: 'jar']
    }
}
