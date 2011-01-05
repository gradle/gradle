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

import static org.junit.Assert.*

import org.gradle.BuildResult
import org.gradle.GradleLauncher

import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.plugins.EmbeddableJavaProject
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.Convention
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.PersistentStateCache
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.jmock.api.Action
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock)
class BuildSourceBuilderTest {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    BuildSourceBuilder buildSourceBuilder
    GradleLauncherFactory gradleFactoryMock = context.mock(GradleLauncherFactory.class)
    GradleLauncher gradleMock = context.mock(GradleLauncher.class)
    Project rootProjectMock = context.mock(Project.class)
    FileCollection configurationMock = context.mock(FileCollection.class)
    TestFile rootDir = tmpDir.createDir('root')
    File testBuildSrcDir = rootDir.file('buildSrc').createDir()
    Set testDependencies
    StartParameter expectedStartParameter
    CacheRepository cacheRepository = context.mock(CacheRepository.class)
    PersistentStateCache stateCache = context.mock(PersistentStateCache.class)
    BuildResult expectedBuildResult
    Gradle build = context.mock(Gradle.class)
    EmbeddableJavaProject projectMetaInfo = context.mock(EmbeddableJavaProject.class)

    @Before public void setUp() {
        buildSourceBuilder = new BuildSourceBuilder(gradleFactoryMock, context.mock(ClassLoaderFactory.class), cacheRepository)
        expectedStartParameter = new StartParameter(currentDir: testBuildSrcDir)
        testDependencies = ['dep1' as File, 'dep2' as File]
        Convention convention = context.mock(Convention)
        context.checking {
            allowing(build).getRootProject(); will(returnValue(rootProjectMock))
            allowing(build).getStartParameter(); will(returnValue(expectedStartParameter))
            allowing(rootProjectMock).getConvention(); will(returnValue(convention))
            allowing(convention).getPlugin(EmbeddableJavaProject);
            will(returnValue(projectMetaInfo))
            allowing(projectMetaInfo).getRuntimeClasspath(); will(returnValue(configurationMock))
            allowing(configurationMock).getFiles(); will(returnValue(testDependencies))
        }
        expectedBuildResult = new BuildResult(build, null)
    }

    @Test public void testCreateClasspathWhenBuildSrcDirExistsAndContainsBuildScript() {
        expectValueFetchedFromCache(null)
        context.checking {
            one(projectMetaInfo).getRebuildTasks(); will(returnValue(['clean', 'build']))
            one(gradleFactoryMock).newInstance((StartParameter) withParam(notNullValue()));
            will(returnValue(gradleMock))
            one(gradleMock).addListener(withParam(not(nullValue()))); will(notifyProjectsEvaluated())
            one(gradleMock).run(); will(returnValue(expectedBuildResult))
        }
        expectValueWrittenToCache()
        
        createBuildFile()
        Set<File> actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateClasspathWhenBuildSrcDirExistsAndDoesNotContainBuildScript() {
        expectValueFetchedFromCache(null)
        context.checking {
            one(projectMetaInfo).getRebuildTasks(); will(returnValue(['clean', 'build']))
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
        expectValueWrittenToCache()

        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateClasspathWhenBuildSrcDirExistsAndHasBeenBuiltBefore() {
        expectValueFetchedFromCache(true)
        context.checking {
            one(projectMetaInfo).getBuildTasks(); will(returnValue(['build']))
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
        expectValueWrittenToCache()

        Set actualClasspath = buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter)
        assertEquals(testDependencies, actualClasspath)
    }

    @Test public void testCreateClasspathWhenBuildSrcDirDoesNotExist() {
        expectedStartParameter = expectedStartParameter.newInstance()
        expectedStartParameter.setCurrentDir(new File('nonexisting'));
        assertEquals([] as Set, buildSourceBuilder.createBuildSourceClasspath(expectedStartParameter))
    }

    private expectValueFetchedFromCache(def value) {
        context.checking {
            CacheBuilder builder = context.mock(CacheBuilder.class)
            PersistentCache cache = context.mock(PersistentCache.class)
            one(cacheRepository).cache('buildSrc')
            will(returnValue(builder))

            one(builder).forObject(testBuildSrcDir)
            will(returnValue(builder))

            one(builder).invalidateOnVersionChange()
            will(returnValue(builder))

            one(builder).open()
            will(returnValue(cache))

            one(cache).openStateCache()
            will(returnValue(stateCache))

            one(stateCache).get()
            will(returnValue(value))
        }
    }

    private expectValueWrittenToCache() {
        context.checking {
            one(stateCache).set(true)
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
