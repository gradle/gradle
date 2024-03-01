/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests

import org.apache.commons.compress.archivers.tar.TarFile
import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarOutputStream
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class TarSlipIntegrationTest extends AbstractIntegrationSpec {

    private TestFile getEvilTar() {
        file("evil.tar.bz")
    }

    def setup() {
        evilTar.withOutputStream {
            new TarOutputStream(it).withCloseable { TarOutputStream tos ->
                TarEntry entry = new TarEntry('../../tmp/evil.sh')
                byte[] bytes = 'evil'.getBytes('utf-8')
                entry.size = bytes.length
                tos.putNextEntry(entry)
                tos.write(bytes)
                tos.closeEntry()
            }
        }
    }

    def "evil tar has path traversal"() {
        given:
        def entryNames = new TarFile(evilTar).withCloseable {
            it.entries.collect { it.name }
        }

        expect:
        entryNames == ['../../tmp/evil.sh']
    }

    def "Copy task refuses to unpack evil tar"() {
        executer.withStacktraceEnabled()

        given:
        buildFile << '''
            task copyEvilTar(type: Copy) {
                from(tarTree('evil.tar.bz'))
                into('.')
            }
        '''

        when:
        fails 'copyEvilTar'

        then:
        failureDescriptionContains "Execution failed for task ':copyEvilTar'"
        failure.assertHasErrorOutput "'../../tmp/evil.sh' is not a safe archive entry or path name"
    }
}
