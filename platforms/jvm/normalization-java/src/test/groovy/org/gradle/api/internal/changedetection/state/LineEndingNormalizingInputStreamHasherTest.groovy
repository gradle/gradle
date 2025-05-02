/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import spock.lang.Specification
import spock.lang.TempDir

import org.gradle.api.internal.changedetection.state.LineEndingContentFixture as content

class LineEndingNormalizingInputStreamHasherTest extends Specification {
    @TempDir
    File tempDir

    def hasher = new LineEndingNormalizingInputStreamHasher()

    def "can normalize line endings in files (eol = '#description')"() {
        def unnormalized = file('unnormalized.txt') << content.textWithLineEndings(eol)
        def normalized = file('normalized.txt') << content.textWithLineEndings('\n')

        expect:
        hasher.hashContent(unnormalized).get() == hasher.hashContent(normalized).get()

        where:
        eol     | description
        '\r'    | 'CR'
        '\r\n'  | 'CR-LF'
        '\n'    | 'LF'
    }

    def "can normalize line endings in input streams (eol = '#description')"() {
        def unnormalized = inputStream(content.textWithLineEndings(eol))
        def normalized = inputStream(content.textWithLineEndings('\n'))

        expect:
        hasher.hashContent(unnormalized).get() == hasher.hashContent(normalized).get()

        where:
        eol     | description
        '\r'    | 'CR'
        '\r\n'  | 'CR-LF'
        '\n'    | 'LF'
    }

    def "does not normalize content for binary files with #description"() {
        def file = file('foo') << contents

        expect:
        !hasher.hashContent(file).isPresent()

        where:
        description               | contents
        "png content"             | content.PNG_CONTENT
        "jpg content"             | content.JPG_CONTENT
        "java class file content" | content.CLASS_FILE_CONTENT
    }

    def "does not normalize content for binary input streams with #description"() {
        def stream = inputStream(contents)

        expect:
        !hasher.hashContent(stream).isPresent()

        where:
        description               | contents
        "png content"             | content.PNG_CONTENT
        "jpg content"             | content.JPG_CONTENT
        "java class file content" | content.CLASS_FILE_CONTENT
    }

    File file(String path) {
        return new File(tempDir, path)
    }

    static InputStream inputStream(String content) {
        return inputStream(content.bytes)
    }

    static InputStream inputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes)
    }
}
