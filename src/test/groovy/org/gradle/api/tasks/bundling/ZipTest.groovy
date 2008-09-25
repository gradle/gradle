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

package org.gradle.api.tasks.bundling

import groovy.mock.interceptor.MockFor
import org.gradle.api.tasks.AbstractTaskTest
import org.junit.Before
import static org.junit.Assert.*
import org.junit.Test;


/**
 * @author Hans Dockter
 */
class ZipTest extends AbstractArchiveTaskTest {
    Zip zip

    MockFor antZipMocker

    @Before public void setUp()  {
        super.setUp()
        zip = new Zip(project, AbstractTaskTest.TEST_TASK_NAME)
        configure(zip)
        antZipMocker = new MockFor(AntZip)
    }

    AbstractArchiveTask getArchiveTask() {
        zip
    }

    MockFor getAntMocker(boolean toBeCalled) {
        antZipMocker.demand.execute(toBeCalled ? 1..1 : 0..0) {AntArchiveParameter archiveParameter ->
            if (toBeCalled) {
                checkArchiveParameterEqualsArchive(archiveParameter, zip)
            }
        }
        antZipMocker
    }

    def getAnt() {
        zip.antZip
    }

    @Test public void testZip() {
        zip = new Zip(project, AbstractTaskTest.TEST_TASK_NAME)
        assertEquals(Zip.ZIP_EXTENSION, zip.extension)
        checkConstructor()
    }

    List getFileSetMethods() {
        super.getFileSetMethods() + ['zipFileSet']
    }


}