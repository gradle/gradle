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

import org.gradle.util.HelperUtil
import org.gradle.Main

/**
 * @author Hans Dockter
 */
class ImportsReaderTest extends GroovyTestCase {
    static final String TEST_DEFAULT_IMPORTS = '''import a.b.*
'''
    static final String TEST_PROJECT_IMPORTS = '''import c.d.*
'''
    ImportsReader testObj
    File testDir
    File testDefaultImportsFile
    File testProjectImportsFile


    void setUp() {
        testDir = HelperUtil.makeNewTestDir()
        (testDefaultImportsFile = new File(testDir, 'defaultImports')).write(TEST_DEFAULT_IMPORTS)
        (testProjectImportsFile = new File(testDir, Main.IMPORTS_FILE_NAME)).write(TEST_PROJECT_IMPORTS)
        testObj = new ImportsReader(testDefaultImportsFile)
    }

    void testInit() {
        assertEquals(testDefaultImportsFile, testObj.defaultImportsFile)
    }

    void testReadImports() {
        Map result = testObj.getImports(testDir)
        assertEquals(TEST_DEFAULT_IMPORTS + TEST_PROJECT_IMPORTS, result.text)
        assertEquals(2, result.importsLineCount)
    }

    void testReadImportsWithNullDefaultImportsFile() {
        testObj.defaultImportsFile = null
        Map result = testObj.getImports(testDir)
        assertEquals(TEST_PROJECT_IMPORTS, result.text)
        assertEquals(1, result.importsLineCount)
    }

    void testReadImportsWithNonExistingProjectImportsFile() {
        testProjectImportsFile.delete()
        Map result = testObj.getImports(testDir)
        assertEquals(TEST_DEFAULT_IMPORTS, result.text)
        assertEquals(1, result.importsLineCount)
    }

    void testReadImportsWithNonExistingProjectImportsAndNullDefaultsImportsFile() {
        testObj.defaultImportsFile = null
        testProjectImportsFile.delete()
        Map result = testObj.getImports(testDir)
        assertEquals('', result.text)
        assertEquals(0, result.importsLineCount)
    }
}
