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

package org.gradle.api.tasks

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.UncheckedIOException
import org.gradle.api.internal.AbstractTask
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.*

class DirectoryTest extends AbstractTaskTest {
    static final String TASK_DIR_NAME = 'parent/child'
    Directory directoryForAbstractTest
    Directory directory

    public AbstractTask getTask() {
        return directoryForAbstractTest
    }

    @Before public void setUp() {
        directoryForAbstractTest = createTask(Directory.class)
        directory = createTask(Directory.class, project, TASK_DIR_NAME)
    }

    @Test public void testInit() {
        assertEquals(new File(project.projectDir, TASK_DIR_NAME).absoluteFile, directory.dir)
    }

    @Test public void testInitWithAbsolutePathName() {
        try {
            createTask(Directory.class, project, new File('nonRelative').absolutePath)
            fail()
        } catch (GradleException e) {
            assertThat(e.cause, instanceOf(InvalidUserDataException.class))
        }
    }

    @Test public void testExecute() {
        directory.execute()
        assert new File(project.projectDir, TASK_DIR_NAME).isDirectory()
    }

    @Test public void testWithExistingDir() {
        File dir = new File(project.projectDir, TASK_DIR_NAME)
        dir.mkdirs()
        // create new file to check later that dir has not been recreated 
        File file = new File(dir, 'somefile')
        file.createNewFile()
        directory.execute()
        assert dir.isDirectory()
        assert file.isFile()
    }

    @Test (expected = UncheckedIOException) public void testWithExistingFile() {
        File file = new File(project.projectDir, 'testname')
        file.createNewFile()
        directory = createTask(Directory.class, project, 'testname')
        directory.mkdir()
    }
}
