/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.invocation.Gradle
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.*
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.jmock.api.Action
import org.gradle.api.plugins.Convention
import org.gradle.api.internal.plugins.EmbedableJavaProject
import org.gradle.groovy.scripts.StringScriptSource

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock)
class BuildSourceBuilderTest {
    BuildSourceBuilder buildSourceBuilder
    GradleLauncherFactory gradleFactoryMock
    GradleLauncher gradleMock
    Project rootProjectMock
    Configuration configurationMock
    CacheInvalidationStrategy cacheInvalidationStrategyMock
    File rootDir
    File testBuildSrcDir
    Set testDependencies
    StartParameter expectedStartParameter
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    String expectedArtifactPath
    BuildResult expectedBuildResult
    Gradle build
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        File testDir = tmpDir.dir
        (rootDir = new File(testDir, 'root')).mkdir()
        (testBuildSrcDir = new File(rootDir, 'buildSrc')).mkdir()
        gradleFactoryMock = context.mock(GradleLauncherFactory)
        gradleMock = context.mock(GradleLauncher)
        rootProjectMock = context.mock(Project)
        configurationMock = context.mock(Configuration)
        cacheInvalidationStrategyMock = context.mock(CacheInvalidationStrategy)
        buildSourceBuilder = new BuildSourceBuilder(gradleFactoryMock, cacheInvalidationStrategyMock)
        expectedStartParameter = new StartParameter(
                searchUpwards: false,
                currentDir: testBuildSrcDir,
                taskNames: ['clean', 'build'],
                gradleUserHomeDir: new File('gradleUserHome'),
                projectProperties: [:]
        )
        testDependencies = ['dep1' as File, 'dep2' as File]
        expectedArtifactPath = "$testBuildSrcDir.absolutePath/build/COMPLETED"
        build = context.mock(Gradle)
        Convention convention = context.mock(Convention)
        EmbedableJavaProject projectMetaInfo = context.mock(EmbedableJavaProject)
        context.checking {
            allowing(build).getRootProject(); will(returnValue(rootProjectMock))
            allowing(build).getStartParameter(); will(returnValue(expectedStartParameter))
            allowing(rootProjectMock).getConvention(); will(returnValue(convention))
            allowing(convention).getPlugin(EmbedableJavaProject);
            will(returnValue(projectMetaInfo))
            allowing(projectMetaInfo).getRebuildTasks(); will(returnValue(['clean', 'dostuff']))
            allowing(projectMetaInfo).getRuntimeClasspath(); will(returnValue(configurationMock))
            allowing(configurationMock).getFiles(); will(returnValue(testDependencies))
        }
        expectedBuildResult = new BuildResult(build, null)
    }

    @Test public void testBuildSourceBuilder() {
        assert buildSourceBuilder.gradleLauncherFactory.is(gradleFactoryMock)
    }

    @Test public void testBuildArtifactFile() {
        assertEquals(new File(expectedArtifactPath), buildSourceBuilder.markerFile(testBuildSrcDir))
    }

    @Test public void testCreateDependencyWithExistingBuildSources() {
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        context.checking {
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(false))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).run(); will(returnValue(expectedBuildResult))
        }
        createArtifact()
        createBuildFile()
        Set<File> actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithCachedArtifactAndValidCache() {
        expectedStartParameter.setCacheUsage(CacheUsage.ON)
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        context.checking {
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(true))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).getBuildAnalysis(); will(returnValue(expectedBuildResult))
        }
        createArtifact()
        createBuildFile()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithCachedArtifactAndValidCacheWithCacheOff() {
        expectedStartParameter.setCacheUsage(CacheUsage.OFF)
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        context.checking {
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(true))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).run(); will(returnValue(expectedBuildResult))
        }
        createArtifact()
        createBuildFile()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithNonExistingBuildScript() {
        StartParameter modifiedStartParameter = this.expectedStartParameter.newInstance()
        modifiedStartParameter.setBuildScriptSource(new StringScriptSource("default buildSrc build script", BuildSourceBuilder.getDefaultScript()))
        context.checking {
            allowing(cacheInvalidationStrategyMock).isValid(expectedArtifactPath as File, testBuildSrcDir); will(returnValue(false))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).run(); will(returnValue(expectedBuildResult))
        }
        createArtifact()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithNonExistingBuildSrcDir() {
        expectedStartParameter = expectedStartParameter.newInstance()
        expectedStartParameter.setCurrentDir(new File('nonexisting'));
        assertEquals([] as Set, buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter))
    }

    private createBuildFile() {
        new File(testBuildSrcDir, Project.DEFAULT_BUILD_FILE).createNewFile()
    }

    private createArtifact() {
        buildSourceBuilder.markerFile(testBuildSrcDir).parentFile.mkdirs()
        buildSourceBuilder.markerFile(testBuildSrcDir).createNewFile()
    }

    private Action notifyProjectsEvaluated() {
        return [invoke: {invocation -> invocation.getParameter(0).projectsEvaluated(build)},
            describeTo: {description -> }] as Action
    }
}
