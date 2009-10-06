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
import org.gradle.api.internal.project.ImportsReader
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.gradle.groovy.scripts.*
import org.gradle.initialization.*
import static org.gradle.util.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class ScriptEvaluatingSettingsProcessorTest {
    static final File TEST_ROOT_DIR = new File('rootDir')
    static final File TEST_CURRENT_DIR = new File('currentDir')
    ScriptEvaluatingSettingsProcessor settingsProcessor
    DefaultSettingsFinder expectedSettingsFinder
    ImportsReader importsReader
    SettingsFactory settingsFactory
    StartParameter expectedStartParameter
    ScriptCompilerFactory scriptProcessorMock
    DefaultSettings expectedSettings
    MockFor settingsFactoryMocker
    ScriptSource scriptSourceMock
    IGradlePropertiesLoader propertiesLoaderMock
    Map expectedGradleProperties
    URLClassLoader urlClassLoader

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        instantiateConstructorArgs()
        settingsProcessor = new ScriptEvaluatingSettingsProcessor(scriptProcessorMock, importsReader, settingsFactory)
        initSettingsFinder()
        expectedStartParameter = new StartParameter()
        expectedGradleProperties = [a: 'b']
        propertiesLoaderMock = [getGradleProperties: { expectedGradleProperties } ] as IGradlePropertiesLoader
        urlClassLoader = new URLClassLoader(new URL[0]);
        initExpectedSettings()
    }

    private void instantiateConstructorArgs() {
        scriptProcessorMock = context.mock(ScriptCompilerFactory)
        importsReader = new ImportsReader()
        settingsFactory = context.mock(SettingsFactory)
    }

    private void initExpectedSettings() {
        expectedSettings = new DefaultSettings()
        DefaultProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
        expectedSettings.setRootProjectDescriptor(new DefaultProjectDescriptor(null, TEST_ROOT_DIR.name,
                TEST_ROOT_DIR, projectDescriptorRegistry))
        expectedSettings.setProjectDescriptorRegistry(projectDescriptorRegistry)
        expectedSettings.setStartParameter(expectedStartParameter)
        context.checking {
            one(settingsFactory).createSettings(TEST_ROOT_DIR, scriptSourceMock, expectedGradleProperties, expectedStartParameter, urlClassLoader)
            will(returnValue(expectedSettings))
        }
    }

    private void initSettingsFinder() {
        expectedSettingsFinder = new DefaultSettingsFinder()
        scriptSourceMock = context.mock(ScriptSource)
    }

    @Test public void testSettingsProcessor() {
        assertSame(scriptProcessorMock, settingsProcessor.scriptProcessor)
        assert settingsProcessor.importsReader.is(importsReader)
        assert settingsProcessor.settingsFactory.is(settingsFactory)
    }

    @Test public void testProcessWithSettingsFile() {
        expectedStartParameter.setCurrentDir(TEST_ROOT_DIR)
        prepareScriptProcessorMock()
        context.checking {
            allowing(scriptSourceMock).getText(); will(returnValue(""))
        }
        SettingsLocation settingsLocation = new SettingsLocation(TEST_ROOT_DIR, scriptSourceMock)
        assertSame(expectedSettings, settingsProcessor.process(settingsLocation, urlClassLoader, expectedStartParameter, propertiesLoaderMock))
    }

    private void prepareScriptProcessorMock() {
        ScriptSource expectedScriptSource = new ImportsScriptSource(scriptSourceMock, importsReader, TEST_ROOT_DIR);
        ScriptRunner scriptRunnerMock = context.mock(ScriptRunner)
        ScriptCompiler processorMock = context.mock(ScriptCompiler)
        context.checking {
            one(scriptProcessorMock).createCompiler(withParam(reflectionEquals(expectedScriptSource)))
            will(returnValue(processorMock))

            one(processorMock).setClassloader(urlClassLoader)
            one(processorMock).compile(SettingsScript.class)
            will(returnValue(scriptRunnerMock))

            one(scriptRunnerMock).setDelegate(expectedSettings)

            one(scriptRunnerMock).run()
        }
    }
}
