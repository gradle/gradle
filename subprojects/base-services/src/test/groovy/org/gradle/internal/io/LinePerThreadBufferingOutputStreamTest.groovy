/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.io

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.internal.TextUtil

class LinePerThreadBufferingOutputStreamTest extends ConcurrentSpec {
    def interleavesLinesFromEachThread() {
        def output = [].asSynchronized()
        TextStream action = { String line -> output << line.replace(TextUtil.platformLineSeparator, "<EOL>") } as TextStream
        def outstr = new LinePerThreadBufferingOutputStream(action)

        when:
        async {
            10.times {
                start {
                    100.times {
                        outstr.write('write '.getBytes())
                        outstr.print(it)
                        outstr.println()
                    }
                }
            }
        }

        then:
        output.size() == 1000
        output.findAll({!it.matches('write \\d+<EOL>')}).empty
    }

    def flushForwardsBufferedText() {
        TextStream action = Mock()
        def outstr = new LinePerThreadBufferingOutputStream(action)

        when:
        outstr.print("text")

        then:
        0 * action._

        when:
        outstr.flush()

        then:
        1 * action.text("text")
        0 * action._
    }

    def flushDoesNothingWhenNothingWritten() {
        TextStream action = Mock()
        def outstr = new LinePerThreadBufferingOutputStream(action)

        when:
        outstr.flush()

        then:
        0 * action._
    }

    def closeForwardsBufferedText() {
        TextStream action = Mock()
        def outstr = new LinePerThreadBufferingOutputStream(action)

        when:
        outstr.print("text")

        then:
        0 * action._

        when:
        outstr.close()

        then:
        1 * action.text("text")
        1 * action.endOfStream(null)
        0 * action._
    }

    def closeDoesNothingWhenNothingWritten() {
        TextStream action = Mock()
        def outstr = new LinePerThreadBufferingOutputStream(action)

        when:
        outstr.close()

        then:
        1 * action.endOfStream(null)
        0 * action._
    }

    def "can reuse the output stream on the same thread after it has been closed"() {
        TextStream action = Mock()
        def outstr = new LinePerThreadBufferingOutputStream(action)

        when:
        outstr.write("text".bytes)
        outstr.close()

        then:
        1 * action.text("text")

        when:
        outstr.write("text".bytes)
        outstr.close()

        then:
        1 * action.text("text")
    }
}
