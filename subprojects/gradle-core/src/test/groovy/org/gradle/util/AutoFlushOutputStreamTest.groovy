/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util

import spock.lang.Specification

class AutoFlushOutputStreamTest extends Specification {
    final OutputStream target = Mock()
    final AutoFlushOutputStream stream = new AutoFlushOutputStream(target)

    def flushesAfterSingleCharacterWrite() {
        when:
        stream.write(5)

        then:
        1 * target.write(5)
        1 * target.flush()
        0 * target._
    }

    def flushesAfterBufferWrite() {
        def buffer = "string" as byte[]

        when:
        stream.write(buffer)

        then:
        1 * target.write(buffer)
        1 * target.flush()
        0 * target._
    }

    def flushesAfterPartialBufferWrite() {
        def buffer = "string" as byte[]

        when:
        stream.write(buffer, 4, 6)

        then:
        1 * target.write(buffer, 4, 6)
        1 * target.flush()
        0 * target._
    }
}
