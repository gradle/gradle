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

package org.gradle.api.internal.project

import org.gradle.api.GradleScriptException
import org.gradle.api.InputStreamClassLoader
import org.gradle.api.internal.project.BuildScriptProcessor
import org.gradle.api.internal.project.DefaultProject

/**
 * @author Hans Dockter
 */
class BuildScriptProcessorTest extends GroovyTestCase {
    static final String TEST_BUILD_FILE_NAME = 'somename'

    static final String TEST_IMPORTS = '''import org.gradle.api.*
'''
    BuildScriptProcessor buildScriptProcessor

    DefaultProject dummyProject

    boolean projectMethodCalled

    ClassLoader classLoader

    Script projectScript

    File testRootDir

    ImportsReader mockImportsReader


    void setUp() {
        testRootDir = new File("/psth/root")
        mockImportsReader = [getImports: {File rootDir ->
            assertEquals(testRootDir, rootDir)
            TEST_IMPORTS
        }] as ImportsReader
        classLoader = new InputStreamClassLoader()
        InputStream inputStream = this.getClass().getResourceAsStream('/org/gradle/api/ClasspathTester.dat')
        classLoader.loadClass("org.gradle.api.ClasspathTester", inputStream)
        buildScriptProcessor = new BuildScriptProcessor(mockImportsReader)
        buildScriptProcessor.classLoader = classLoader
        dummyProject = [someProjectMethod: {projectMethodCalled = true}, getRootDir: {testRootDir},
                getServices: {HelperUtil.createTestProjectService()}, getName: {'projectname'},
                setProjectScript: {Script script -> projectScript = script}] as DefaultProject

        projectMethodCalled = false
    }

    void testInit() {
        assert buildScriptProcessor.importsReader.is(mockImportsReader)
    }

    void testEvaluate() {
        String scriptCode = '''
// We leave out the path to check import adding   
new ClasspathTester().testMethod()

def scriptMethod() { 'scriptMethod' }
    someProjectMethod()
    scriptProperty = project.path + 'mySuffix'
    String internalProp = 'a'
    assert internalProp == 'a'
    newProperty = 'a'
    assert newProperty == 'a'
    assert newProperty == project.newProperty
    def x = bindingProperty // leads to an exception if bindingProperty is not available 
'''
        dummyProject.buildScriptFinder = mockBuildScriptFinder(scriptCode)
        buildScriptProcessor.evaluate(dummyProject, [bindingProperty: 'somevalue'])
        assertTrue(projectMethodCalled)
        assertEquals("scriptMethod", projectScript.scriptMethod())
        assertEquals(dummyProject.path + 'mySuffix', projectScript.scriptProperty)
        assertEquals(dummyProject.path + 'mySuffix', dummyProject.additionalProperties['scriptProperty'])
    }

    void testWithEmptyScript() {
        dummyProject.buildScriptFinder = mockBuildScriptFinder('')
        buildScriptProcessor.evaluate(dummyProject)
        assertNotNull(projectScript)
    }

    void testWithException() {
        try {
            dummyProject.buildScriptFinder = mockBuildScriptFinder('unknownProp')
            buildScriptProcessor.evaluate(dummyProject)
            fail()
        } catch (GradleScriptException e) {
            assert e.cause instanceof MissingPropertyException
        }
    }

    BuildScriptFinder mockBuildScriptFinder(String scriptText) {
        [getBuildScript: {project -> scriptText}, getBuildFileName: {TEST_BUILD_FILE_NAME}] as BuildScriptFinder
    }
}