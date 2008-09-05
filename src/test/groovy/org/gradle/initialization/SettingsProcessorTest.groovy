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
import org.gradle.StartParameter
import org.gradle.api.DependencyManager
import org.gradle.api.Project
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.dependencies.DependencyManagerFactory
import org.gradle.api.internal.project.ImportsReader
import org.gradle.groovy.scripts.*
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.ParentDirSettingsFinder
import org.gradle.initialization.SettingsProcessor
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.ReflectionEqualsMatcher
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.After
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.GradleScriptException

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock.class)
class SettingsProcessorTest {

    static final File TEST_ROOT_DIR = new File('rootDir')
    SettingsProcessor settingsProcessor
    ISettingsFinder expectedSettingsFinder
    ImportsReader importsReader
    DependencyManagerFactory dependencyManagerFactory
    SettingsFactory settingsFactory
    BuildSourceBuilder buildSourceBuilder
    StartParameter expectedStartParameter
    File buildResolverDir
    File settingsFileDir

    IScriptProcessor scriptProcessorMock
    ISettingsScriptMetaData settingsScriptMetaData
    ScriptSource settingsScript
    Script expectedScript

    DefaultSettings expectedSettings
    MockFor settingsFactoryMocker

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        scriptProcessorMock = context.mock(IScriptProcessor)
        settingsScriptMetaData = context.mock(ISettingsScriptMetaData)
        buildResolverDir = HelperUtil.makeNewTestDir('buildResolver')
        settingsFileDir = HelperUtil.makeNewTestDir('settingsDir')
        expectedSettings = new DefaultSettings()
        expectedStartParameter = new StartParameter()
        expectedSettingsFinder = new ParentDirSettingsFinder()
        importsReader = context.mock(ImportsReader)
        settingsFactory = context.mock(SettingsFactory)
        settingsScript = context.mock(ScriptSource.class)
        expectedScript = context.mock(Script.class)
        dependencyManagerFactory = new DefaultDependencyManagerFactory(new File('root'))
        buildSourceBuilder = new BuildSourceBuilder()
        settingsProcessor = new SettingsProcessor(settingsScriptMetaData, scriptProcessorMock, importsReader, settingsFactory, dependencyManagerFactory, buildSourceBuilder,
                buildResolverDir)
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
    }

    @Test public void testSettingsProcessor() {
        assertSame(settingsScriptMetaData, settingsProcessor.settingsScriptMetaData)
        assertSame(scriptProcessorMock, settingsProcessor.scriptProcessor)
        assertThat(settingsProcessor.importsReader, sameInstance(importsReader))
        assertThat(settingsProcessor.settingsFactory, sameInstance(settingsFactory))
        assertThat(settingsProcessor.dependencyManagerFactory, sameInstance(dependencyManagerFactory))
        assertThat(settingsProcessor.buildSourceBuilder, sameInstance(buildSourceBuilder))
        assertThat(settingsProcessor.buildResolverDir, sameInstance(buildResolverDir))
    }

    @Test public void testCreateBasicSettings() {
        File expectedCurrentDir = new File(TEST_ROOT_DIR, 'currentDir')
        expectedStartParameter = createStartParameter(expectedCurrentDir)
        prepareSettingsFactoryMock(expectedCurrentDir, expectedCurrentDir)
        assert settingsProcessor.createBasicSettings(expectedSettingsFinder, expectedStartParameter).is(expectedSettings)
        assertEquals([], expectedSettings.projectPaths)
        checkBuildResolverDir(buildResolverDir)
    }

    @Test public void testWithNonExistingBuildResolverDir() {
        HelperUtil.deleteTestDir()
        File expectedCurrentDir = new File(TEST_ROOT_DIR, 'currentDir')
        expectedStartParameter = createStartParameter(expectedCurrentDir)
        prepareSettingsFactoryMock(expectedCurrentDir, expectedCurrentDir)
        assert settingsProcessor.createBasicSettings(expectedSettingsFinder, expectedStartParameter).is(expectedSettings)
        assertEquals([], expectedSettings.projectPaths)
        checkBuildResolverDir(buildResolverDir)
    }

    @Test public void testProcessWithCurrentDirAsSubproject() {
        File currentDir = new File(TEST_ROOT_DIR, 'currentDir')
        assertSame(expectedSettings, runCUT(TEST_ROOT_DIR, currentDir, ['currentDir', 'path2'], buildResolverDir))
    }

    @Test public void testProcessWithCurrentDirNotASubprojectOfRootProject() {
        File currentDir = new File(TEST_ROOT_DIR, 'currentDir')
        assertSame(expectedSettings, runCUT(TEST_ROOT_DIR, currentDir, ['path1', 'path2'], buildResolverDir) {
            prepareSettingsFactoryMock(currentDir, currentDir)
        })
    }

    @Test public void testProcessWithCurrentDirAsRootDir() {
        assertSame(expectedSettings, runCUT(TEST_ROOT_DIR, TEST_ROOT_DIR, ['path1', 'path2'], buildResolverDir))
    }

    @Test public void testProcessWithNullBuildResolver() {
        settingsProcessor.buildResolverDir = null
        assertSame(expectedSettings, runCUT(TEST_ROOT_DIR, TEST_ROOT_DIR, ['path1', 'path2'],
                new File(TEST_ROOT_DIR, Project.TMP_DIR_NAME + "/" + DependencyManager.BUILD_RESOLVER_NAME)))
    }

    @Test public void testWrapsScriptEvaluationFailure() {
        RuntimeException failure = new RuntimeException()
        File currentDir = new File(TEST_ROOT_DIR, 'currentDir')
        ScriptSource expectedScriptSource = new ImportsScriptSource(settingsScript, importsReader, TEST_ROOT_DIR);

        prepareDependencies(TEST_ROOT_DIR, [], currentDir)
        context.checking {
            one(scriptProcessorMock).createScript(
                    withParam(notNullValue(ScriptSource.class)),
                    withParam(notNullValue(ClassLoader.class)),
                    withParam(equalTo(Script.class)))
            will(returnValue(expectedScript))
            one(settingsScriptMetaData).applyMetaData(expectedScript, expectedSettings)
            one(expectedScript)
            will(throwException(failure))
        }

        try {
            settingsProcessor.process(expectedSettingsFinder, expectedStartParameter)
            fail()
        } catch (GradleScriptException e) {
            assertThat(e.originalMessage, equalTo("A problem occurred evaluating the settings file."))
            assertThat(e.scriptSource, ReflectionEqualsMatcher.reflectionEquals(expectedScriptSource))
            assertThat(e.cause, equalTo(failure))
        }
    }

    private void prepareSettingsFactoryMock(File expectedRootDir, File expectedCurrentDir) {
        expectedSettings.settingsFinder = expectedSettingsFinder
        expectedSettings.startParameter = expectedStartParameter
        context.checking {
            one(settingsFactory).createSettings(dependencyManagerFactory, buildSourceBuilder, 
                    expectedSettingsFinder, expectedStartParameter)
            will(returnValue(expectedSettings))
        }
    }

    private DefaultSettings runCUT(File rootDir, File currentDir, List includePaths, File expectedBuildResolverRoot,
                                   Closure customSettingsFactoryPreparation = {}) {

        StartParameter expectedStartParameter = prepareDependencies(rootDir, includePaths, currentDir, customSettingsFactoryPreparation)

        ScriptSource expectedScriptSource = new ImportsScriptSource(settingsScript, importsReader, rootDir);

        context.checking {
            one(scriptProcessorMock).createScript(
                    withParam(ReflectionEqualsMatcher.reflectionEquals(expectedScriptSource)),
                    withParam(sameInstance(Thread.currentThread().contextClassLoader)),
                    withParam(equalTo(Script.class)))
            will(returnValue(expectedScript))
            one(settingsScriptMetaData).applyMetaData(expectedScript, expectedSettings)
            one(expectedScript)
        }

        DefaultSettings settings = settingsProcessor.process(expectedSettingsFinder, expectedStartParameter)
        checkBuildResolverDir(expectedBuildResolverRoot)
        settings
    }

    private StartParameter prepareDependencies(File rootDir, List includePaths, File currentDir, Closure customSettingsFactoryPreparation = {}) {
        expectedSettingsFinder.settingsDir = new File(rootDir.path)
        expectedSettingsFinder.settingsScript = settingsScript
        expectedStartParameter = createStartParameter(currentDir)
        expectedSettings.setProjectPaths(includePaths)
        prepareSettingsFactoryMock(rootDir, currentDir)
        customSettingsFactoryPreparation()
        return expectedStartParameter
    }

    private void checkBuildResolverDir(File buildResolverDir) {
        assertEquals(buildResolverDir, settingsProcessor.dependencyManagerFactory.buildResolverDir)
        assert !buildResolverDir.exists()
    }

    StartParameter createStartParameter(File currentDir) {
        StartParameter startParameter = StartParameter.newInstance(expectedStartParameter);
        startParameter.setGradleUserHomeDir(new File('gradleUserHomeDir'))
        startParameter.setCurrentDir(currentDir);
        startParameter
    }
}