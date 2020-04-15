/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.cache.internal.btree

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ByteInputTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    RandomAccessFile file
    ByteInput input

    def setup() {
        file = new RandomAccessFile(tmpDir.file("test.bin"), "rw")
        input = new ByteInput(file)
    }

    def cleanup() {
        file.close()
    }

    def "can reuse to read from multiple locations in file"() {
        given:
        file.seek(0)
        file.writeInt(123)
        file.writeInt(321)
        file.writeInt(456)

        expect:
        def stream = input.start(0)
        stream.readInt() == 123
        stream.readInt() == 321
        input.done()

        def stream2 = input.start(4)
        stream2.readInt() == 321
        stream2.readInt() == 456
        input.done()

        def stream3 = input.start(0)
        stream3.readInt() == 123
        input.done()
    }

    def "cannot read beyond end of file"() {
        when:
        input.start(123).readInt()

        then:
        EOFException e = thrown()
    }
}
