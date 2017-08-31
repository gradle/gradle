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
package org.gradle.internal.hash

import java.util.concurrent.TimeUnit

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Assume
import org.junit.Rule

import com.google.common.base.Charsets

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

class DefaultFileHasherTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def file = tmpDir.createFile("testfile")
    DefaultFileHasher hasher
    DefaultStreamHasher streamHasher

    def setup() {
        streamHasher = new DefaultStreamHasher(new DefaultContentHasherFactory())
        hasher = new DefaultFileHasher(streamHasher)
    }

    def "empty file returns correct hash"() {
        def hash = getStringHash("")

        when:
        def result = hasher.hash(file)

        then:
        file.length() == 0
        result == hash

        and:
        0 * _._
    }

    @Issue("gradle/gradle#2552")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    def "named pipe returns correct hash"() {
        def hash = getStringHash("")
        def pipe = new File(tmpDir.createDir("testdir"), "testpipe")
        createPipe(pipe.absolutePath)

        when:
        def result = hasher.hash(pipe)

        then:
        pipe.length() == 0
        result == hash

        and:
        0 * _._
    }

    def "non-empty file returns correct hash"() {
        file.write("hello world")
        def hash = getStringHash("hello world")

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        0 * _._
    }

    def createPipe(def name) {
        def supportFifo = false
        try {
            def mkfifo = new ProcessBuilder("mkfifo", name).redirectErrorStream(true).start()
            def exitValue = mkfifo.waitFor()
            supportFifo = (exitValue == 0)
        } catch (IOException e) {
            supportFifo = false
        }
        // Skip test if mkfifo doesn't work on this platform:
        Assume.assumeTrue(supportFifo)
    }

    def getStringHash(String text) {
        return streamHasher.hash(new ByteArrayInputStream(text.getBytes(Charsets.UTF_8)))
    }
}
