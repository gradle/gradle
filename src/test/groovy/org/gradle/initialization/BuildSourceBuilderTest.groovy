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

import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.After
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.GradleFactory
import org.gradle.Gradle
import org.gradle.BuildListener
import org.gradle.api.DependencyManager
import org.gradle.api.dependencies.Dependency
import org.gradle.api.plugins.JavaPlugin

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
class BuildSourceBuilderTest {
    BuildSourceBuilder buildSourceBuilder
    GradleFactory gradleFactoryMock
    Gradle gradleMock
    Project rootProjectMock
    DependencyManager dependencyManagerMock
    File rootDir
    File testBuildSrcDir
    File testBuildResolverDir
    List testDependencies
    StartParameter expectedStartParameter
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    String expectedArtifactPath

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        File testDir = HelperUtil.makeNewTestDir()
        (rootDir = new File(testDir, 'root')).mkdir()
        (testBuildSrcDir = new File(rootDir, 'buildSrc')).mkdir()
        (testBuildResolverDir = new File(testDir, 'testBuildResolverDir')).mkdir()
        gradleFactoryMock = context.mock(GradleFactory)
        gradleMock = context.mock(Gradle)
        rootProjectMock = context.mock(Project)
        dependencyManagerMock = context.mock(DependencyManager)
        buildSourceBuilder = new BuildSourceBuilder(gradleFactoryMock)
        expectedStartParameter = new StartParameter(
                searchUpwards: true,
                currentDir: testBuildSrcDir,
                buildFileName: Project.DEFAULT_BUILD_FILE,
                taskNames: ['task1', 'task2'],
                gradleUserHomeDir: new File('gradleUserHome'),
                projectProperties: dependencyProjectProps
        )
        testDependencies = ['dep1' as File, 'dep2' as File]
        expectedArtifactPath = "$testBuildResolverDir.absolutePath/$BuildSourceBuilder.BUILD_SRC_ORG" +
                "/$BuildSourceBuilder.BUILD_SRC_MODULE/$BuildSourceBuilder.BUILD_SRC_REVISION/jars/${BuildSourceBuilder.BUILD_SRC_MODULE}.jar"
        context.checking {
            allowing(rootProjectMock).getDependencies(); will(returnValue(dependencyManagerMock))
            allowing(dependencyManagerMock).getBuildResolverDir(); will(returnValue(testBuildResolverDir))
        }
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
    }

    @Test public void testBuildSourceBuilder() {
        assert buildSourceBuilder.gradleFactory.is(gradleFactoryMock)
    }

    @Test public void testBuildArtifactFile() {
        assertEquals(new File(expectedArtifactPath), buildSourceBuilder.buildArtifactFile(testBuildResolverDir))
    }

    @Test public void testCreateDependencyWithExistingBuildSources() {
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        modifiedStartParameter.setSearchUpwards(false)
        context.checking {
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
            one(dependencyManagerMock).resolve(JavaPlugin.RUNTIME); will(returnValue(testDependencies))
        }
        createArtifact()
        createBuildFile()
        List actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(new HashSet(testDependencies + [expectedArtifactPath as File]), new HashSet(actualClasspath))
    }

    @Test public void testCreateDependencyWithNonExistingBuildScript() {
        StartParameter modifiedStartParameter = this.expectedStartParameter.newInstance()
        modifiedStartParameter.setSearchUpwards(false)
        modifiedStartParameter.useEmbeddedBuildFile(BuildSourceBuilder.getDefaultScript())
        context.checking {
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
            one(dependencyManagerMock).resolve(JavaPlugin.RUNTIME); will(returnValue(testDependencies))
        }
        createArtifact()
        List actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(new HashSet(testDependencies + [expectedArtifactPath as File]), new HashSet(actualClasspath))
    }

    @Test public void testCreateDependencyWithNonExistingBuildSrcDir() {
        expectedStartParameter = expectedStartParameter.newInstance()
        expectedStartParameter.setCurrentDir(new File('nonexisting'));
        assertEquals([], buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter))
    }

    @Test public void testCreateDependencyWithNoArtifactProducingBuild() {
        StartParameter modifiedStartParameter = this.expectedStartParameter.newInstance()
        modifiedStartParameter.setSearchUpwards(false)
        context.checking {
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
            allowing(dependencyManagerMock).resolve(JavaPlugin.RUNTIME); will(returnValue(testDependencies))
        }
        createBuildFile()
        assertEquals([], buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter))
    }

    @Test public void testCreateDependencyWithEmptyTaskList() {
        createBuildFile()
        expectedStartParameter = expectedStartParameter.newInstance()
        expectedStartParameter.setTaskNames([])
        assertEquals([], buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter))
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
