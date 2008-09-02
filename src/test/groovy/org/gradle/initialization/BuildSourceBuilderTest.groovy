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
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.After
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser;

/**
 * @author Hans Dockter
 */
class BuildSourceBuilderTest {
    BuildSourceBuilder buildSourceBuilder
    EmbeddedBuildExecuter embeddedBuildExecuter
    File rootDir
    File testBuildSrcDir
    File testBuildResolverDir
    StartParameter expectedStartParameter
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        File testDir = HelperUtil.makeNewTestDir()
        (rootDir = new File(testDir, 'root')).mkdir()
        (testBuildSrcDir = new File(rootDir, 'buildSrc')).mkdir()
        (testBuildResolverDir = new File(testDir, 'testBuildResolverDir')).mkdir()
        embeddedBuildExecuter = context.mock(EmbeddedBuildExecuter)
        buildSourceBuilder = new BuildSourceBuilder(embeddedBuildExecuter)
        expectedStartParameter = new StartParameter(
                searchUpwards: true,
                currentDir: testBuildSrcDir,
                buildFileName: Project.DEFAULT_BUILD_FILE,
                taskNames: ['task1', 'task2'],
                gradleUserHomeDir: new File('gradleUserHome'),
                projectProperties: dependencyProjectProps
        )
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
    }

    @Test public void testBuildSourceBuilder() {
        assert buildSourceBuilder.embeddedBuildExecuter.is(embeddedBuildExecuter)
    }

    @Test public void testBuildArtifactFile() {
        String expectedPath = "$testBuildResolverDir.absolutePath/$BuildSourceBuilder.BUILD_SRC_ORG" +
                "/$BuildSourceBuilder.BUILD_SRC_MODULE/$BuildSourceBuilder.BUILD_SRC_REVISION/jars/${BuildSourceBuilder.BUILD_SRC_MODULE}.jar"
        assertEquals(new File(expectedPath), buildSourceBuilder.buildArtifactFile(testBuildResolverDir))
    }

    @Test public void testCreateDependencyWithExistingBuildSources() {
        StartParameter expectedStartParameter = StartParameter.newInstance(this.expectedStartParameter)
        expectedStartParameter.setSearchUpwards(false)
        context.checking {
            one(embeddedBuildExecuter).execute(testBuildResolverDir, expectedStartParameter)
        }
        createArtifact()
        createBuildFile()
        def result = buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter)
        assertEquals(result, BuildSourceBuilder.BUILD_SRC_ID)
    }

    @Test public void testCreateDependencyWithNonExistingBuildScript() {
        StartParameter expectedStartParameter = StartParameter.newInstance(this.expectedStartParameter)
        expectedStartParameter.setSearchUpwards(false)
        context.checking {
            one(embeddedBuildExecuter).executeEmbeddedScript(testBuildResolverDir, BuildSourceBuilder.getDefaultScript(), expectedStartParameter)
        }
        createArtifact()
        def result = buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter)
        assertEquals(result, BuildSourceBuilder.BUILD_SRC_ID)
    }

    @Test public void testCreateDependencyWithNonExistingBuildSrcDir() {
        expectedStartParameter = StartParameter.newInstance(expectedStartParameter)
        expectedStartParameter.setCurrentDir(new File('nonexisting'));
        assertNull(buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter))
    }

    @Test public void testCreateDependencyWithNoArtifactProducingBuild() {
        context.checking {
            one(embeddedBuildExecuter).executeEmbeddedScript(withParam(any(File)), withParam(any(String)), withParam(any(StartParameter)))
        }
        assertNull(buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter))
    }

    @Test public void testCreateDependencyWithEmptyTaskList() {
        createBuildFile()
        expectedStartParameter = StartParameter.newInstance(expectedStartParameter)
        expectedStartParameter.setTaskNames([])
        assertNull(buildSourceBuilder.createDependency(testBuildResolverDir, expectedStartParameter))
    }

    private createBuildFile() {
        new File(testBuildSrcDir, Project.DEFAULT_BUILD_FILE).createNewFile()
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
