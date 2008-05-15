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

import org.gradle.Main
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class ImportsReaderTest extends GroovyTestCase {
    static final String NL = System.properties['line.separator']
    
    static final String TEST_DEFAULT_IMPORTS = "import a.b.*$NL"
    static final String TEST_PROJECT_IMPORTS = "import c.d.*$NL"

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
        String result = testObj.getImports(testDir)
        assertEquals(TEST_DEFAULT_IMPORTS + TEST_PROJECT_IMPORTS, result)
    }

    void testReadImportsWithNullDefaultImportsFile() {
        testObj.defaultImportsFile = null
        String result = testObj.getImports(testDir)
        assertEquals(TEST_PROJECT_IMPORTS, result)
    }

    void testReadImportsWithNonExistingProjectImportsFile() {
        testProjectImportsFile.delete()
        String result = testObj.getImports(testDir)
        assertEquals(TEST_DEFAULT_IMPORTS, result)
    }

    void testReadImportsWithNonExistingProjectImportsAndNullDefaultsImportsFile() {
        testObj.defaultImportsFile = null
        testProjectImportsFile.delete()
        String result = testObj.getImports(testDir)
        assertEquals('', result)
    }
}
