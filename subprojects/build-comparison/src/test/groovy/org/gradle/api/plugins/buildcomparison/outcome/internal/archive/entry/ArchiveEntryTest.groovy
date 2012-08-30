/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry

import spock.lang.Specification

class ArchiveEntryTest extends Specification {

    def "equals and hash code"() {
        when:
        def a1 = new ArchiveEntry(
                path: "foo",
                size: 10,
                crc: 10,
                directory: true
        )
        def a2 = new ArchiveEntry(
                path: "foo",
                size: 10,
                crc: 10,
                directory: true
        )

        then:
        a1 == a2
        a2 == a1
        a1.hashCode() == a2.hashCode()

        when:
        a2.size = 20

        then:
        a1 != a2
        a2 != a1
        a1.hashCode() != a2.hashCode()
    }
}
