/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.internal.plugins.EmbeddableJavaProject
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.Convention
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.api.Action
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.cache.CacheRepository
import org.gradle.util.TimeProvider
import org.gradle.cache.CacheBuilder
import org.gradle.cache.PersistentStateCache
import org.gradle.cache.PersistentCache

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
    CacheRepository cacheRepositoryMock
    PersistentStateCache cacheMock
    TimeProvider timeProviderMock
    File rootDir
    File testBuildSrcDir
    Set testDependencies
    StartParameter expectedStartParameter
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    Long expectedTimestamp
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
        cacheRepositoryMock = context.mock(CacheRepository)
        cacheMock = context.mock(PersistentStateCache)
        timeProviderMock = context.mock(TimeProvider)
        buildSourceBuilder = new BuildSourceBuilder(gradleFactoryMock, cacheInvalidationStrategyMock, context.mock(ClassLoaderFactory), cacheRepositoryMock, timeProviderMock)
        expectedStartParameter = new StartParameter(
                searchUpwards: false,
                currentDir: testBuildSrcDir,
                taskNames: ['clean', 'build'],
                gradleUserHomeDir: new File('gradleUserHome'),
                projectProperties: [:]
        )
        testDependencies = ['dep1' as File, 'dep2' as File]
        expectedTimestamp = 2000L
        build = context.mock(Gradle)
        Convention convention = context.mock(Convention)
        EmbeddableJavaProject projectMetaInfo = context.mock(EmbeddableJavaProject)
        context.checking {
            allowing(build).getRootProject(); will(returnValue(rootProjectMock))
            allowing(build).getStartParameter(); will(returnValue(expectedStartParameter))
            allowing(rootProjectMock).getConvention(); will(returnValue(convention))
            allowing(convention).getPlugin(EmbeddableJavaProject);
            will(returnValue(projectMetaInfo))
            allowing(projectMetaInfo).getRebuildTasks(); will(returnValue(['clean', 'dostuff']))
            allowing(projectMetaInfo).getRuntimeClasspath(); will(returnValue(configurationMock))
            allowing(configurationMock).getFiles(); will(returnValue(testDependencies))
        }
        expectedBuildResult = new BuildResult(build, null)
    }

    @Test public void testCreateDependencyWithExistingBuildSources() {
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        expectTimestampFetchedFromCache()
        context.checking {
            one(cacheInvalidationStrategyMock).isValid(expectedTimestamp, testBuildSrcDir); will(returnValue(false))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).run(); will(returnValue(expectedBuildResult))
        }
        expectTimestampWrittenToCache()
        
        createBuildFile()
        Set<File> actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithCachedArtifactAndValidCache() {
        expectedStartParameter.setCacheUsage(CacheUsage.ON)
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        expectTimestampFetchedFromCache()
        context.checking {
            one(cacheInvalidationStrategyMock).isValid(expectedTimestamp, testBuildSrcDir); will(returnValue(true))
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).getBuildAnalysis(); will(returnValue(expectedBuildResult))
        }
        expectTimestampWrittenToCache()

        createBuildFile()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithCachedArtifactAndValidCacheWithRebuildCache() {
        expectedStartParameter.setCacheUsage(CacheUsage.REBUILD)
        StartParameter modifiedStartParameter = expectedStartParameter.newInstance()
        expectTimestampFetchedFromCache()
        context.checking {
            one(gradleFactoryMock).newInstance(modifiedStartParameter); will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).run(); will(returnValue(expectedBuildResult))
        }
        expectTimestampWrittenToCache()

        createBuildFile()
        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithNonExistingBuildScript() {
        expectTimestampFetchedFromCache()
        context.checking {
            one(cacheInvalidationStrategyMock).isValid(expectedTimestamp, testBuildSrcDir); will(returnValue(false))
            one(gradleFactoryMock).newInstance((StartParameter) withParam(notNullValue()))
            will { StartParameter param ->
                assertThat(param.buildScriptSource, instanceOf(StringScriptSource.class))
                assertThat(param.buildScriptSource.displayName, equalTo('default buildSrc build script'))
                assertThat(param.buildScriptSource.resource.text, equalTo(BuildSourceBuilder.defaultScript))
                return gradleMock
            }
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).run(); will(returnValue(expectedBuildResult))
        }
        expectTimestampWrittenToCache()

        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateDependencyWithNonExistingBuildSrcDir() {
        expectedStartParameter = expectedStartParameter.newInstance()
        expectedStartParameter.setCurrentDir(new File('nonexisting'));
        assertEquals([] as Set, buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter))
    }

    private expectTimestampFetchedFromCache() {
        context.checking {
            CacheBuilder cacheBuilder = context.mock(CacheBuilder)
            PersistentCache cache = context.mock(PersistentCache)

            one(cacheRepositoryMock).cache('buildSrc')
            will(returnValue(cacheBuilder))

            one(cacheBuilder).forObject(testBuildSrcDir)
            will(returnValue(cacheBuilder))

            one(cacheBuilder).invalidateOnVersionChange()
            will(returnValue(cacheBuilder))

            one(cacheBuilder).open()
            will(returnValue(cache))

            one(cache).openStateCache()
            will(returnValue(cacheMock))

            one(cacheMock).get()
            will(returnValue(expectedTimestamp))
        }
    }

    private expectTimestampWrittenToCache() {
        context.checking {
            allowing(timeProviderMock).getCurrentTime()
            will(returnValue(6700L))

            one(cacheMock).set(6700L)
        }
    }

    private createBuildFile() {
        new File(testBuildSrcDir, Project.DEFAULT_BUILD_FILE).createNewFile()
    }

    private Action notifyProjectsEvaluated() {
        return [invoke: {invocation -> invocation.getParameter(0).projectsEvaluated(build)},
                describeTo: {description -> }] as Action
    }
}
