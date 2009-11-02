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

import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class TarTest extends AbstractArchiveTaskTest {
    Tar tar

    @Before public void setUp()  {
        super.setUp()
        tar = createTask(Tar)
        configure(tar)
    }

    AbstractArchiveTask getArchiveTask() {
        tar
    }

    @Test public void testTar() {
        assert tar.compression.is(Compression.NONE)
        assert tar.longFile.is(LongFile.WARN)
        tar.compression = Compression.BZIP2
        assertEquals(Tar.TAR_EXTENSION, tar.extension)
    }
}