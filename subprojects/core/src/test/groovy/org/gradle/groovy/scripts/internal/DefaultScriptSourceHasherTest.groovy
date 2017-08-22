/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.groovy.scripts.internal

import com.google.common.base.Charsets
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.hash.FileContentHasherFactory
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.resource.TextResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultScriptSourceHasherTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def hash = HashCode.fromInt(123)

    def fileHasher = Mock(FileHasher)
    def contentHasherFactory = Mock(FileContentHasherFactory)
    def scriptHasher = new DefaultScriptSourceHasher(fileHasher, contentHasherFactory)

    def hashesBackingFileWhenResourceIsBackedByFile() {
        def script = Mock(ScriptSource)
        def resource = Mock(TextResource)
        def file = tmpDir.createFile("testfile")

        when:
        def result = scriptHasher.hash(script)

        then:
        result == hash

        and:
        1 * script.resource >> resource
        1 * resource.file >> file
        1 * fileHasher.hash(file) >> hash
        0 * _
    }

    def hashesContentWhenResourceIsNotBackedByFile() {
        def script = Mock(ScriptSource)
        def resource = Mock(TextResource)
        def hasher = Mock(Hasher)

        when:
        def result = scriptHasher.hash(script)

        then:
        result == hash

        and:
        1 * script.resource >> resource
        1 * resource.file >> null
        1 * resource.text >> "alma"
        1 * contentHasherFactory.create() >> hasher
        1 * hasher.putString("alma", Charsets.UTF_8)
        1 * hasher.hash() >> hash
        0 * _
    }
}
