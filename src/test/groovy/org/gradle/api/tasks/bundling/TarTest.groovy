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

/**
 * @author Hans Dockter
 */
class TarTest extends AbstractArchiveTaskTest {
    Tar tar

    MockFor antTarMocker

    void setUp() {
        super.setUp()
        tar = new Tar(project, AbstractTaskTest.TEST_TASK_NAME)
        configure(tar)
        antTarMocker = new MockFor(AntTar)
    }

    AbstractArchiveTask getArchiveTask() {
        tar
    }

    void testTar() {
        assert tar.compression.is(Compression.NONE)
        assert tar.longFile.is(LongFile.WARN)
        tar.compression = Compression.BZIP2
        assertEquals(Tar.TAR_EXTENSION + Compression.BZIP2.extension, tar.extension)
    }

    MockFor getAntMocker(boolean toBeCalled) {
        antTarMocker.demand.execute(toBeCalled ? 1..1 : 0..0) {AntArchiveParameter archiveParameter, Compression compression, LongFile longFile ->
            if (toBeCalled) {
                checkArchiveParameterEqualsArchive(archiveParameter, tar)
                assertEquals(tar.compression, compression)
                assertEquals(tar.longFile, longFile)
            }
        }
        antTarMocker
    }

    def getAnt() {
        tar.antTar
    }

    List getFileSetMethods() {
        super.getFileSetMethods() + ['tarFileSet']
    }

}