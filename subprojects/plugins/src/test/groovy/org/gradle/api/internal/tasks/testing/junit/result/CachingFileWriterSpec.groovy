/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result

import com.esotericsoftware.kryo.io.Output
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 11/19/12
 */
class CachingFileWriterSpec extends Specification {

    private @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    private writer = new CachingFileWriter(2)

    def cleanup() {
        writer.closeAll()
    }

    def "keeps n files open"() {
        when:
        writer.write(temp.file("1.txt"), "1", "1")

        then:
        writer.openFiles.keySet()*.name == ["1.txt"]

        when:
        writer.write(temp.file("2.txt"), "2", "2")

        then:
        writer.openFiles.keySet()*.name == ["1.txt", "2.txt"]

        when:
        writer.write(temp.file("3.txt"), "3", "3")

        then:
        writer.openFiles.keySet()*.name == ["2.txt", "3.txt"]

        when:
        writer.closeAll()

        then:
        writer.openFiles.isEmpty()
    }

    def "writes to files"() {
        when:
        writer.write(temp.file("1.txt"), "1", "1")
        writer.write(temp.file("2.txt"), "2", "2")
        writer.write(temp.file("3.txt"), "3", "3")
        writer.write(temp.file("4.txt"), "4", "4")
        writer.write(temp.file("1.txt"), "a", "a")
        writer.write(temp.file("2.txt"), "b", "b")
        writer.write(temp.file("3.txt"), "c", "c")

        and:
        writer.closeAll()

        then:
        writer.openFiles.isEmpty()

        and:
        temp.file("1.txt").bytes == serialize('1', '1', 'a', 'a')
        temp.file("2.txt").bytes == serialize('2', '2', 'b', 'b')
        temp.file("3.txt").bytes == serialize('3', '3', 'c', 'c')
        temp.file("4.txt").bytes == serialize('4', '4')
    }

    byte[] serialize(String... strings) {
        def baos = new ByteArrayOutputStream()
        def output = new Output(baos)
        strings.each { output.writeString(it) }
        output.close()
        baos.toByteArray()
    }
}
