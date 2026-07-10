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

class ByteOutputTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    RandomAccessFile file
    ByteOutput output

    def setup() {
        file = new RandomAccessFile(tmpDir.file("test.bin"), "rw")
        output = new ByteOutput(file)
    }

    def cleanup() {
        file.close()
    }

    def "can reuse to write to different locations in backing file"() {
        when:
        def stream = output.start(0)
        stream.writeInt(123)
        stream.writeByte(12)
        output.done()

        then:
        file.length() == 5
        file.seek(0)
        file.readInt() == 123
        file.readByte() == 12

        when:
        stream = output.start(5)
        stream.writeInt(321)
        output.done()

        then:
        file.length() == 9
        file.seek(0)
        file.readInt() == 123
        file.readByte() == 12
        file.readInt() == 321
    }

    def "can reuse to write to same location in backing file"() {
        when:
        def stream = output.start(0)
        stream.writeInt(123)
        stream.writeByte(12)
        output.done()

        then:
        file.length() == 5
        file.seek(0)
        file.readInt() == 123
        file.readByte() == 12

        when:
        stream = output.start(0)
        stream.writeInt(321)
        output.done()

        then:
        file.length() == 5
        file.seek(0)
        file.readInt() == 321
        file.readByte() == 12
    }

    def "can start writing beyond end of file"() {
        when:
        def stream = output.start(10)
        stream.writeInt(123)
        stream.writeByte(12)
        output.done()

        then:
        file.length() == 15
        file.seek(10)
        file.readInt() == 123
        file.readByte() == 12
    }
}
