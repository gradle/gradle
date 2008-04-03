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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.Clean
import org.gradle.util.HelperUtil
import org.gradle.api.tasks.util.ExistingDirsFilter

/**
 * @author Hans Dockter
 */
class CleanTest extends AbstractConventionTaskTest {
    Clean clean

    void setUp() {
        super.setUp()
        clean = new Clean(project, AbstractTaskTest.TEST_TASK_NAME)
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    Task getTask() {
        clean
    }

    void testClean() {
        assertNull(clean.dir)
    }

    void testExecute() {
        clean.dir = HelperUtil.makeNewTestDir()
        clean.existingDirsFilter = [checkExistenceAndThrowStopActionIfNot: {File dir ->
            assertEquals(clean.dir, dir)
        }] as ExistingDirsFilter
        (new File(clean.dir, 'somefile')).createNewFile()
        clean.execute()
        assertFalse(clean.dir.exists())
    }

    void testExecuteWithNullDir() {
        shouldFailWithCause(InvalidUserDataException) {
            clean.execute()
        }
    }

}
