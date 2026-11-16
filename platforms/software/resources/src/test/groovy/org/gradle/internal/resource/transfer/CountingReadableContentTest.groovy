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

package org.gradle.internal.resource.transfer

import org.gradle.internal.resource.ReadableContent
import spock.lang.Specification

class CountingReadableContentTest extends Specification {
    static final int LENGTH = 20

    def reported = []
    def content = new CountingReadableContent(source(), { long bytes -> reported << bytes })

    def "counts the bytes read"() {
        when:
        content.open().withStream { it.readAllBytes() }

        then:
        content.count == LENGTH
        reported.sum() == LENGTH
    }

    def "counts each byte once when the content is read again"() {
        when:
        content.open().withStream { it.readAllBytes() }
        content.open().withStream { it.readAllBytes() }

        then:
        content.count == LENGTH
        reported.sum() == LENGTH
    }

    def "counts only the bytes past an earlier, partial read"() {
        when:
        content.open().withStream { it.readNBytes(8) }
        content.open().withStream { it.readAllBytes() }

        then:
        content.count == LENGTH
        reported == [8L, 12L]
    }

    def "counts each part once when parts are read in order after skipping to them"() {
        when:
        3.times { part ->
            // Like a transport that signs each part before it sends it
            2.times {
                content.open().withStream {
                    it.skip(part * 7)
                    it.readNBytes(7)
                }
            }
        }

        then:
        content.count == LENGTH
        reported == [7L, 7L, 6L]
    }

    def "does not count skipped bytes"() {
        when:
        content.open().withStream {
            it.skip(5)
            it.readNBytes(5)
        }

        then:
        content.count == 5
    }

    def "counts bytes read one at a time"() {
        when:
        content.open().withStream { stream -> 3.times { stream.read() } }
        content.open().withStream { stream -> 4.times { stream.read() } }

        then:
        content.count == 4
        reported == [1L, 1L, 1L, 1L]
    }

    def "does not support mark and reset"() {
        expect:
        !content.open().markSupported()
    }

    def "uses the length of the content"() {
        expect:
        content.contentLength == LENGTH
    }

    private static ReadableContent source() {
        new ReadableContent() {
            @Override
            InputStream open() { new ByteArrayInputStream(new byte[LENGTH]) }

            @Override
            long getContentLength() { LENGTH }
        }
    }
}
