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

package org.gradle.performance.fixture

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class WaitingReaderTest extends ConcurrentSpec {

    def input = new ExpandingReader()
    def source = new BufferedReader(input)

    def "can read lines"() {
        def reader = new WaitingReader(source, 100, 1)
        input.append("1\n2\n")

        expect:
        reader.readLine() == "1"
        reader.retriedCount == 0

        reader.readLine() == "2"
        reader.retriedCount == 0

        reader.readLine() == null
        reader.retriedCount > 0
    }

    def "can receive content after end of stream reached"() {
        def reader = new WaitingReader(source, 100, 1)
        input.append("1\n2\n")

        expect:
        reader.readLine() == "1"
        reader.readLine() == "2"
        reader.readLine() == null
        input.append("3\n4")
        reader.readLine() == "3"
        reader.readLine() == "4"
        reader.readLine() == null
    }

    def "test"() {
        def reader = new WaitingReader(source, 1000, 10)
        input.append("first part of the line")

        start {
            sleep(200)
            input.append(", second part of the line\nnext line\n")
        }

        expect:
        reader.readLine() == "first part of the line, second part of the line"
        reader.readLine() == "next line"
    }

    static class ExpandingReader extends Reader {
        final buffer = new StringBuilder()

        void append(String content) {
            buffer.append(content)
        }

        @Override
        void close() throws IOException {
        }

        @Override
        synchronized int read(char[] dest, int offset, int len) throws IOException {
            if (buffer.length() == 0) {
                return -1
            }
            int count = Math.min(len, buffer.length())
            for (int i = 0; i < count; i++) {
                dest[i + offset] = buffer.charAt(i)
            }
            buffer.delete(0, count)
            return count
        }
    }
}
