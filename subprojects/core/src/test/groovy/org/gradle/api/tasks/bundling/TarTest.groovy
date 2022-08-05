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

class TarTest extends AbstractArchiveTaskTest {
    Tar tar

    def setup()  {
        tar = createTask(Tar)
        configure(tar)
    }

    @Override
    AbstractArchiveTask getArchiveTask() {
        tar
    }

    def "default values"() {
        expect:
        tar.compression == Compression.NONE
        tar.archiveExtension.get() == 'tar'
    }

    def "compression determines default extension"() {
        when:
        tar.compression = Compression.GZIP

        then:
        tar.archiveExtension.get() == 'tgz'

        when:
        tar.compression = Compression.BZIP2

        then:
        tar.archiveExtension.get() == 'tbz2'

        when:
        tar.compression = Compression.NONE

        then:
        tar.archiveExtension.get() == 'tar'

        when:
        tar.archiveExtension.set('bin')
        tar.compression = Compression.GZIP

        then:
        tar.archiveExtension.get() == 'bin'
    }
}
