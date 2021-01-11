/*
 * Copyright 2020 the original author or authors.
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

import spock.lang.Specification
import spock.lang.Unroll


class LineEndingNormalizingInputStreamTest extends Specification {
    @Unroll
    def "can normalize line endings in input streams (eol = '#eol')"() {
        def bytes = inputStream("${eol}This is a line${eol}Another line${eol}${eol}Yet another line\nAnd one more\n\nAnd yet one more${eol}").readAllBytes()

        expect:
        bytes == '\nThis is a line\nAnother line\n\nYet another line\nAnd one more\n\nAnd yet one more\n'.bytes

        where:
        eol << ['\r', '\r\n', '\n']
    }

    InputStream inputStream(String input) {
        return new LineEndingNormalizingInputStream(new ByteArrayInputStream(input.bytes))
    }
}
