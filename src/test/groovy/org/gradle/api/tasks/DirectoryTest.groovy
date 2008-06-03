/*                                               is
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

package org.gradle.api.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class DirectoryTest extends AbstractTaskTest {
    static final String TASK_DIR_NAME = 'parent/child'
    Directory directoryForAbstractTest
    Directory directory

    public Task getTask() {
        return directoryForAbstractTest
    }

    void setUp() {
        super.setUp()
        directoryForAbstractTest = new Directory(project, AbstractTaskTest.TEST_TASK_NAME)
        directory = new Directory(project, TASK_DIR_NAME)
        HelperUtil.makeNewTestDir()
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testInit() {
        assertEquals(new File(project.projectDir, TASK_DIR_NAME).absoluteFile, directory.dir)
    }

    void testInitWithAbsolutePathName() {
        shouldFailWithCause(InvalidUserDataException) {
            directory = new Directory(project, new File('nonRelative').absolutePath)
        }
    }

    void testExecute() {
        directory.execute()
        assert new File(project.projectDir, TASK_DIR_NAME).isDirectory()
    }

    void testWithExistingDir() {
        File dir = new File(project.projectDir, TASK_DIR_NAME)
        dir.mkdirs()
        // create new file to check later that dir has not been recreated 
        File file = new File(dir, 'somefile')
        file.createNewFile()
        directory.execute()
        assert dir.isDirectory()
        assert file.isFile()
    }

    void testWithExistingFile() {
        File file = new File(project.projectDir, 'testname')
        file.createNewFile()
        directory = new Directory(project, 'testname')
        shouldFailWithCause(InvalidUserDataException) {
            directory.execute()
        }
    }

    void testToString() {
        assertEquals(directory.dir.absolutePath, directory.toString())
    }
}
