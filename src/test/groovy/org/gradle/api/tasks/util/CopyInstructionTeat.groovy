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

package org.gradle.api.tasks.util

import org.gradle.api.InvalidUserDataException
import org.gradle.util.HelperUtil
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;


/**
* @author Hans Dockter
*/
class CopyInstructionTest {
    CopyInstruction copyInstruction
    File sourceDir
    File targetDir
    Set includes
    Set excludes
    File f1Dat
    File f2Txt
    File f3Jpg

    @Before public void setUp()  {
        File rootDir = HelperUtil.makeNewTestDir()
        sourceDir = createDir(new File(rootDir, 'sourceDir'))
        File resourceDir11 = createDir(new File(sourceDir.absolutePath + "/org/gradle/", "package1"))
        File resourceDir12 = createDir(new File(sourceDir.absolutePath + "/org/gradle/", "package2"))
        File resourceDir13 = createDir(new File(sourceDir.absolutePath + "/org/gradle/", "package3"))

        f1Dat = createFile(new File(resourceDir11, 'f1.dat'))
        f2Txt = createFile(new File(resourceDir12, 'f2.txt'))
        f3Jpg = createFile(new File(resourceDir13, 'f13.jpg'))

        targetDir = new File(rootDir, 'targetDir')

        copyInstruction = new CopyInstruction(sourceDir: sourceDir, targetDir: targetDir)
    }

    @Test public void testCopyInstruction() {
        assertEquals([] as HashSet, copyInstruction.excludes)
        assertEquals([] as HashSet, copyInstruction.includes)
        assertEquals([:], copyInstruction.filters)


    }

    @Test public void testInclude() {
        copyInstruction.includes = ["**/*.dat"]
        copyInstruction.execute()
        List files = []
        targetDir.eachFileRecurse {files << it}
        assertEquals(4, files.size())
        assertTrue(new File("${targetDir.absolutePath}/org/gradle/package1", f1Dat.name).isFile())
    }

    @Test public void testExclude() {
        copyInstruction.excludes = ["org/gradle/package2", "**/*.txt", "org/gradle/package3", "**/*.jpg"]
        copyInstruction.execute()
        List files = []
        targetDir.eachFileRecurse {files << it}
        assertEquals(4, files.size())
        assertTrue(new File("${targetDir.absolutePath}/org/gradle/package1", f1Dat.name).isFile())
    }

    @Test public void testWithoutIncludeExclude() {
        copyInstruction.execute()
        List files = []
        targetDir.eachFileRecurse {files << it}
        assertEquals(8, files.size())
        assertTrue(new File("${targetDir.absolutePath}/org/gradle/package1", f1Dat.name).isFile())
        assertTrue(new File("${targetDir.absolutePath}/org/gradle/package2", f2Txt.name).isFile())
        assertTrue(new File("${targetDir.absolutePath}/org/gradle/package3", f3Jpg.name).isFile())
    }

    @Test public void testFilter() {
        String token1 = 'token1'
        String token2 = 'token2'
        String value1 = 'value1'
        String value2 = 'value2'
        String f2Text = "@$token1@ whatever @$token2@"
        f2Txt.write(f2Text)
        copyInstruction.includes = ["**/*.txt"]
        copyInstruction.filters = [(token1): value1, (token2): value2]
        copyInstruction.execute()
        String newText = new File("${targetDir.absolutePath}/org/gradle/package2", f2Txt.name).text
        assertEquals(f2Text.replace("@$token1@", value1).replace("@$token2@", value2), newText)
    }

    @Test(expected = InvalidUserDataException) public void testExecuteWithNullSourceDir() {
            new CopyInstruction(sourceDir: null, targetDir: new File("/dir")).execute()
    }

    @Test(expected = InvalidUserDataException) public void testExecuteWithMissingTargetDir() {
            new CopyInstruction(sourceDir: new File("/dir")).execute()
    }

    @Test(expected = InvalidUserDataException) public void testExecuteWithMissingSourceDir() {
            new CopyInstruction(targetDir: new File("/dir")).execute()
    }

    @Test(expected = InvalidUserDataException) public void testExecuteWithNullTargetDir() {
            new CopyInstruction(sourceDir: new File("/dir"), targetDir: null).execute()
    }




    private File createFile(File file) {
        file.createNewFile()
        file
    }

    private File createDir(File file) {
        file.mkdirs()
        file
    }

}
