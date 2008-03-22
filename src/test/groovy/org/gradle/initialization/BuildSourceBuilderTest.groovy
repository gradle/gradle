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
import org.gradle.api.DependencyManager
import org.gradle.api.Project
import org.gradle.util.HelperUtil

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

    void setUp() {
        File testDir = HelperUtil.makeNewTestDir()
        (rootDir = new File(testDir, 'root')).mkdir()
        (testBuildSrcDir = new File(rootDir, 'buildSrc')).mkdir()
        (testBuildResolverDir = new File(testDir, 'testBuildResolverDir')).mkdir()
        embeddedBuildExecuter = new EmbeddedBuildExecuter()
        buildSourceBuilder = new BuildSourceBuilder(embeddedBuildExecuter)
        embeddedBuildExecuterMocker = new MockFor(EmbeddedBuildExecuter)
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
        List expectedTaskNames = ['task1', 'task2']
        Map expectedSystemProperties = [prop1: 'prop1']
        Map expectedProjectProperties = [projectProp1: 'projectProp1']
        boolean expectedRecursive = false
        boolean expectedSearchUpwards = false
        embeddedBuildExecuterMocker.demand.execute(1..1) {File buildResolverDir, File projectDir, String buildScriptName, List taskNames,
                                                          Map projectProperties, Map systemProperties,
                                                          boolean recursive, boolean searchUpwards ->
            createArtifact()
            assertEquals(testBuildResolverDir, buildResolverDir)
            assertEquals(testBuildSrcDir, projectDir)
            assertEquals(Project.DEFAULT_PROJECT_FILE, buildScriptName)
            assertEquals(expectedTaskNames, taskNames)
            assertEquals(expectedProjectProperties + dependencyProjectProps, projectProperties)
            assertEquals(expectedSystemProperties, systemProperties)
            assertEquals(expectedRecursive, recursive)
            assertEquals(expectedSearchUpwards, searchUpwards)
        }
        createBuildFile()
        embeddedBuildExecuterMocker.use(embeddedBuildExecuter) {
            def result = buildSourceBuilder.createDependency(testBuildSrcDir, testBuildResolverDir,
                    Project.DEFAULT_PROJECT_FILE, expectedTaskNames,
                    expectedProjectProperties, expectedSystemProperties, expectedRecursive, expectedSearchUpwards)
            assertEquals(result, BuildSourceBuilder.BUILD_SRC_ID)
        }
    }

    void testCreateDependencyWithNonExistingBuildScript() {
        Map expectedSystemProperties = [prop1: 'prop1']
        Map expectedProjectProperties = [projectProp1: 'projectProp1']
        List expectedTaskNames = ['task1', 'task2']
        boolean expectedRecursive = false
        boolean expectedSearchUpwards = false
        embeddedBuildExecuterMocker.demand.executeEmbeddedScript(1..1) {File buildResolverDir, File projectDir, String embeddedScript, List taskNames,
                                                                        Map projectProperties, Map systemProperties ->
            createArtifact()
            assertEquals(testBuildResolverDir, buildResolverDir)
            assertEquals(testBuildSrcDir, projectDir)
            assertEquals(embeddedScript, BuildSourceBuilder.DEFAULT_SCRIPT)
            assertEquals(expectedTaskNames, taskNames)
            assertEquals(expectedProjectProperties + dependencyProjectProps, projectProperties)
            assertEquals(expectedSystemProperties, systemProperties)
        }
        embeddedBuildExecuterMocker.use(embeddedBuildExecuter) {
            def result = buildSourceBuilder.createDependency(testBuildSrcDir, testBuildResolverDir,
                    Project.DEFAULT_PROJECT_FILE, expectedTaskNames,
                    expectedProjectProperties, expectedSystemProperties, expectedRecursive, expectedSearchUpwards)
            assertEquals(result, BuildSourceBuilder.BUILD_SRC_ID)
        }
    }

    void testCreateDependencyWithNonExistingBuildSrcDir() {
        assertNull(buildSourceBuilder.createDependency(new File('nonexisting'), testBuildResolverDir, Project.DEFAULT_PROJECT_FILE, ['sometask'],
                [:], [:], true, true))
    }

    void testCreateDependencyWithNoArtifactProducingBuild() {
        createArtifact()
        embeddedBuildExecuterMocker.demand.executeEmbeddedScript(1..1) {File buildResolverDir, File projectDir, String embeddedScript, List taskNames,
                                                                        Map projectProperties, Map systemProperties ->}
        embeddedBuildExecuterMocker.use(embeddedBuildExecuter) {
            assertNull(buildSourceBuilder.createDependency(testBuildSrcDir, testBuildResolverDir, 'somescript', ['sometask'], [:], [:], true, true))
        }
        assert !new File(testBuildResolverDir, DependencyManager.BUILD_RESOLVER_NAME).exists()
    }

    void testCreateDependencyWithEmptyTaskList() {
        createBuildFile()
        assertNull(buildSourceBuilder.createDependency(testBuildSrcDir, testBuildResolverDir, Project.DEFAULT_PROJECT_FILE, [], [:], [:], true, true))
    }

    void testDeletionOfOldVersion() {
        createArtifact()
        buildSourceBuilder.createDependency(testBuildSrcDir, testBuildResolverDir, 'somescript', [], [:], [:], true, true)
        assert !new File(testBuildResolverDir, DependencyManager.BUILD_RESOLVER_NAME).exists()
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
