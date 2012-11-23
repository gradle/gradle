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

import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 11/19/12
 */
class CachingFileWriterSpec extends Specification {

    private @Rule TemporaryFolder temp = new TemporaryFolder()
    private writer = new CachingFileWriter(2)

    def cleanup() {
        writer.closeAll()
    }

    def "keeps n files open"() {
        when:
        writer.write(temp.file("1.txt"), "1")

        then:
        writer.openFiles.keySet()*.name == ["1.txt"]

        when:
        writer.write(temp.file("2.txt"), "2")

        then:
        writer.openFiles.keySet()*.name == ["1.txt", "2.txt"]

        when:
        writer.write(temp.file("3.txt"), "3")

        then:
        writer.openFiles.keySet()*.name == ["2.txt", "3.txt"]

        when:
        writer.closeAll()

        then:
        writer.openFiles.isEmpty()
    }

    def "writes to files"() {
        when:
        writer.write(temp.file("1.txt"), "1")
        writer.write(temp.file("2.txt"), "2")
        writer.write(temp.file("3.txt"), "3")
        writer.write(temp.file("4.txt"), "4")
        writer.write(temp.file("1.txt"), "a")
        writer.write(temp.file("2.txt"), "b")
        writer.write(temp.file("3.txt"), "c")

        and:
        writer.close(temp.file("xxxx.txt"))
        writer.close(temp.file("1.txt"))
        writer.close(temp.file("2.txt"))
        writer.close(temp.file("3.txt"))
        writer.close(temp.file("4.txt"))

        then:
        writer.openFiles.isEmpty()

        and:
        temp.file("1.txt").text == '1a'
        temp.file("2.txt").text == '2b'
        temp.file("3.txt").text == '3c'
        temp.file("4.txt").text == '4'
    }
}
