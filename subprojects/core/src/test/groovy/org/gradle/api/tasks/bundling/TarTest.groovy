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

import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat;

class TarTest extends AbstractArchiveTaskTest {
    Tar tar

    @Before public void setUp()  {
        tar = createTask(Tar)
        configure(tar)
        tar.from tmpDir.createFile('file.txt')
    }

    AbstractArchiveTask getArchiveTask() {
        tar
    }

    @Test public void testDefaultValues() {
        assertThat(tar.compression, equalTo(Compression.NONE))
        assertThat(tar.extension, equalTo('tar'))
    }
    
    @Test public void testCompressionDeterminesDefaultExtension() {
        tar.compression = Compression.GZIP
        assertThat(tar.extension, equalTo('tgz'))

        tar.compression = Compression.BZIP2
        assertThat(tar.extension, equalTo('tbz2'))

        tar.compression = Compression.NONE
        assertThat(tar.extension, equalTo('tar'))

        tar.extension = 'bin'

        tar.compression = Compression.GZIP
        assertThat(tar.extension, equalTo('bin'))
    }
}