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

import org.gradle.api.DependencyManager
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationResolver
import org.gradle.api.plugins.JavaPlugin
import org.gradle.initialization.BuildListenerAction
import org.gradle.initialization.BuildSourceBuilder
import org.gradle.initialization.CacheInvalidationStrategy
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.*
import static org.junit.Assert.assertEquals

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
class BuildSourceBuilderTest {
    BuildSourceBuilder buildSourceBuilder
    GradleFactory gradleFactoryMock
    Gradle gradleMock
    Project rootProjectMock
    ConfigurationResolver configurationMock
    DependencyManager dependencyManagerMock
    CacheInvalidationStrategy cacheInvalidationStrategyMock
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
        (testBuildResolverDir = new File(testBuildSrcDir, Project.TMP_DIR_NAME + '/' + DependencyManager.BUILD_RESOLVER_NAME)).mkdir()
        gradleFactoryMock = context.mock(GradleFactory)
        gradleMock = context.mock(Gradle)
        rootProjectMock = context.mock(Project)
        dependencyManagerMock = context.mock(DependencyManager)
        configurationMock = context.mock(ConfigurationResolver)
        cacheInvalidationStrategyMock = context.mock(CacheInvalidationStrategy)
        buildSourceBuilder = new BuildSourceBuilder(gradleFactoryMock, cacheInvalidationStrategyMock)
        expectedStartParameter = new StartParameter(
                searchUpwards: true,
                currentDir: testBuildSrcDir,
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
            allowing(dependencyManagerMock).configuration(JavaPlugin.RUNTIME); will(returnValue(configurationMock))
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
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(false))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
            one(configurationMock).resolve(); will(returnValue(testDependencies))
        }
        createArtifact()
        createBuildFile()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(new HashSet(testDependencies + [expectedArtifactPath as File]), new HashSet(actualClasspath))
    }

    @Test public void testCreateDependencyWithCachedArtifactAndValidCache() {
        expectedStartParameter.setCacheUsage(CacheUsage.ON)
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        modifiedStartParameter.setTaskNames([JavaPlugin.INIT])
        modifiedStartParameter.setSearchUpwards(false)
        context.checking {
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(true))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
            one(configurationMock).resolve(); will(returnValue(testDependencies))
        }
        createArtifact()
        createBuildFile()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(new HashSet(testDependencies + [expectedArtifactPath as File]), new HashSet(actualClasspath))
    }

    @Test public void testCreateDependencyWithCachedArtifactAndValidCacheWithCacheOff() {
        expectedStartParameter.setCacheUsage(CacheUsage.OFF)
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        modifiedStartParameter.setSearchUpwards(false)
        context.checking {
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(true))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
            one(configurationMock).resolve(); will(returnValue(testDependencies))
        }
        createArtifact()
        createBuildFile()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(new HashSet(testDependencies + [expectedArtifactPath as File]), new HashSet(actualClasspath))
    }

    @Test public void testCreateDependencyWithNonExistingBuildScript() {
        StartParameter modifiedStartParameter = this.expectedStartParameter.newInstance()
        modifiedStartParameter.setSearchUpwards(false)
        modifiedStartParameter.useEmbeddedBuildFile(BuildSourceBuilder.getDefaultScript())
        context.checking {
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(false))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
            one(configurationMock).resolve(); will(returnValue(testDependencies))
        }
        createArtifact()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
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
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(false))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addBuildListener(withParam(any(BuildListener.class))); will(new BuildListenerAction(rootProjectMock))
            one(gradleMock).run()
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
