/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.process.internal.streams

import org.gradle.internal.SystemProperties
import org.gradle.internal.io.LineBufferingOutputStream
import org.gradle.internal.io.TextStream
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

class ExecOutputHandleRunnerTest extends Specification {
    def "copies contents of stream when run"() {
        def input = new ByteArrayInputStream("hello".bytes)
        def output = new ByteArrayOutputStream()
        def completed = new CountDownLatch(1)
        def runner = new ExecOutputHandleRunner("test", input, output, 4, completed)

        when:
        runner.run()

        then:
        output.toByteArray() == "hello".bytes
        completed.count == 0
    }

    @Issue("GRADLE-3329")
    def "Handles exec output with line containing multi-byte unicode character at buffer boundary"() {
        given:
        def bufferLength = 7
        def text1 = "a" * (bufferLength - 1)
        def text2 = "\u0151" + ("a" * 5)
        def text = text1 + text2
        def action = Mock(TextStream)
        def lineSeparator = SystemProperties.instance.lineSeparator
        def output = new LineBufferingOutputStream(action, lineSeparator)
        def input = new ByteArrayInputStream(text.getBytes("utf-8"))
        def runner = new ExecOutputHandleRunner("test", input, output, bufferLength, new CountDownLatch(1))

        when:
        runner.run()

        then:
        1 * action.text(text1)
        then:
        1 * action.text(text2)
        then:
        1 * action.endOfStream(null)
    }
}
