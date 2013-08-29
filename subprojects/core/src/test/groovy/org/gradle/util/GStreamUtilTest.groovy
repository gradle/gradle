/*
 * Copyright 2009 the original author or authors.
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

class GStreamUtilTest extends Specification {

    def "skips bytes"() {
        def bytes = new ByteArrayOutputStream()
        bytes.write(5)
        bytes.write(10)
        bytes.write(15)
        bytes.write(20)

        when:
        def is = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))
        def skipped = GStreamUtil.skipBytes(2, is)

        then:
        skipped == 2
        is.read() == 15
        GStreamUtil.skipBytes(123, is) == 1
    }
}
