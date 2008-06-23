/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.internal.project

import groovy.mock.interceptor.MockFor
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class BuildScriptProcessorTest extends GroovyTestCase {
    static final String TEST_BUILD_FILE_NAME = 'mybuild.craidle'
    static final String TEST_SCRIPT_TEXT = 'sometext'
    static final String TEST_IMPORTS = '''import org.gradle.api.*
'''

    BuildScriptProcessor buildScriptProcessor

    MockFor scriptCacheHandlerMock

    DefaultProject testProject

    File testProjectDir
    File testBuildScriptFile

    ImportsReader importsReaderMock

    Script expectedScript

    void setUp() {
        testProjectDir = HelperUtil.makeNewTestDir('projectdir')
        testBuildScriptFile = new File(testProjectDir, TEST_BUILD_FILE_NAME)
        testProject = [
                getRootDir: {testProjectDir.parentFile},
                getProjectDir: {testProjectDir},
                getBuildFileName: {TEST_BUILD_FILE_NAME}
        ] as DefaultProject
        importsReaderMock = [getImports: {File rootDir ->
            assertEquals(testProjectDir.parentFile, rootDir)
            TEST_IMPORTS
        }] as ImportsReader
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, null, true)
        buildScriptProcessor.scriptCacheHandler = [:] as ScriptHandler
        scriptCacheHandlerMock = new MockFor(ScriptHandler)
        expectedScript = HelperUtil.createTestScript()
    }

    protected void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testInit() {
        assert buildScriptProcessor.importsReader.is(importsReaderMock)
        assert !buildScriptProcessor.inMemoryScriptText
    }

    void testWithNonExistingBuildFile() {
        scriptCacheHandlerMock.demand.loadFromCache(0..0) {DefaultProject project, long lastModified ->}
        scriptCacheHandlerMock.demand.writeToCache(0..0) {DefaultProject project, String scriptText ->}
        assert buildScriptProcessor.createScript(testProject) instanceof EmptyScript
    }

    void testWithNonCachedExistingBuildScript() {
        createBuildScriptFile()
        scriptCacheHandlerMock.demand.loadFromCache(1..1) {DefaultProject project, long lastModified ->
            assertEquals(testBuildScriptFile.lastModified(), lastModified)
            assert project.is(testProject)
            null
        }
        scriptCacheHandlerMock.demand.writeToCache(1..1) {DefaultProject project, String scriptText ->
            assert project.is(testProject)
            assertEquals(TEST_SCRIPT_TEXT + System.properties['line.separator'] + TEST_IMPORTS, scriptText)
            expectedScript
        }
        scriptCacheHandlerMock.use(buildScriptProcessor.scriptCacheHandler) {
            assert buildScriptProcessor.createScript(testProject).is(expectedScript)
        }
    }

    void testWithCachedBuildScript() {
        createBuildScriptFile()
        scriptCacheHandlerMock.demand.loadFromCache(1..1) {DefaultProject project, long lastModified ->
            assertEquals(testBuildScriptFile.lastModified(), lastModified)
            assert project.is(testProject)
            expectedScript
        }
        scriptCacheHandlerMock.demand.writeToCache(0..0) {DefaultProject project, String scriptText ->}
        scriptCacheHandlerMock.use(buildScriptProcessor.scriptCacheHandler) {
            assert buildScriptProcessor.createScript(testProject).is(expectedScript)
        }
    }

    void testWithInMemoryScriptText() {
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, TEST_SCRIPT_TEXT, true)
        scriptCacheHandlerMock.demand.createScript(1..1) {DefaultProject project, String scriptText ->
            assert project.is(testProject)
            assertEquals(TEST_SCRIPT_TEXT + System.properties['line.separator'] + TEST_IMPORTS, scriptText)
            expectedScript
        }
        scriptCacheHandlerMock.use(buildScriptProcessor.scriptCacheHandler) {
            assert buildScriptProcessor.createScript(testProject).is(expectedScript)
        }
    }

    void testWithNoCache() {
        createBuildScriptFile()
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, null, false)
        scriptCacheHandlerMock.demand.createScript(1..1) {DefaultProject project, String scriptText ->
            assert project.is(testProject)
            assertEquals(TEST_SCRIPT_TEXT + System.properties['line.separator'] + TEST_IMPORTS, scriptText)
            expectedScript
        }
        scriptCacheHandlerMock.use(buildScriptProcessor.scriptCacheHandler) {
            assert buildScriptProcessor.createScript(testProject).is(expectedScript)
        }
    }

    void testWithNoCacheAndNoScript() {
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, null, false)
        assert buildScriptProcessor.createScript(testProject) instanceof EmptyScript
    }

    private void createBuildScriptFile() {
        testBuildScriptFile.write(TEST_SCRIPT_TEXT)
    }

}
