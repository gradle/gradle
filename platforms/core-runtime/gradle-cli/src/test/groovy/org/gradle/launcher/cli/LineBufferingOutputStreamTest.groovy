/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.cli

import spock.lang.Specification

class LineBufferingOutputStreamTest extends Specification {
    def writes = []
    def target = new OutputStream() {
        @Override
        void write(int b) {
            writes << new String([(byte) b] as byte[], "UTF-8")
        }

        @Override
        void write(byte[] b, int off, int len) {
            writes << new String(b, off, len, "UTF-8")
        }
    }
    def stream = new LineBufferingOutputStream(target)

    def "holds back text until its line is complete"() {
        when:
        stream.write("> Task".bytes)
        stream.write(" :hello".bytes)
        stream.flush()

        then:
        writes.empty

        when:
        stream.write("\n".bytes)

        then:
        writes == ["> Task :hello\n"]
    }

    def "passes on the complete lines of a write and holds back the rest"() {
        when:
        stream.write("one\ntwo\nthr".bytes)

        then:
        writes == ["one\ntwo\n"]

        when:
        stream.write("ee\n".bytes)

        then:
        writes == ["one\ntwo\n", "three\n"]
    }

    def "writes the partial line when closed"() {
        when:
        stream.write("no newline".bytes)
        stream.close()

        then:
        writes == ["no newline"]
    }

    def "handles single byte writes and multi-byte characters"() {
        when:
        "é\n".getBytes("UTF-8").each { stream.write(it as int) }

        then:
        writes.join("") == "é\n"
        writes.last().endsWith("\n")
    }
}
