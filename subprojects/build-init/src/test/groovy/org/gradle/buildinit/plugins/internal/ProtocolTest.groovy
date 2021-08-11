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

package org.gradle.buildinit.plugins.internal

import spock.lang.Specification

class ProtocolTest extends Specification {
    def "detects protocol from url"() {
        expect:
            Protocol.fromUrl(url) == protocol

        where:
            protocol        | url
            Protocol.HTTP   | new URL("http://www.google.com")
            Protocol.HTTPS   | new URL("https://www.google.com")
            Protocol.FILE   | new URL("file:///usr/share/test.txt")
    }

    def "secures url using replaceable protocol"() {
        expect:
            Protocol.secureProtocol(url) == secureUrl

        where:
            url                                 | secureUrl
            new URL("http://www.google.com")    | new URL("https://www.google.com")
    }

    def "protocols that can't be secured throw exception"() {
        when:
            Protocol.secureProtocol(url)

        then:
            thrown(IllegalStateException)

        where:
            url << [new URL("https://www.google.com"),
                    new URL("file:///usr/share/test.txt")]
    }
}
