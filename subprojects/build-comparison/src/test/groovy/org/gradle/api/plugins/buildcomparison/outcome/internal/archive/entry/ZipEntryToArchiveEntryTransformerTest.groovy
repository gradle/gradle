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

import java.util.zip.ZipEntry

class ZipEntryToArchiveEntryTransformerTest extends Specification {

    def transformer = new ZipEntryToArchiveEntryTransformer()

    def "transforms file"() {
        given:
        def zip = zipEntry("abc") {
            size = 10
            crc = 10
        }

        when:
        def archiveEntry = transformer.transform(zip)

        then:
        archiveEntry.path == "abc"
        archiveEntry.crc == 10
        !archiveEntry.directory
        archiveEntry.size == 10
    }

    def "transforms directory"() {
        given:
        def zip = zipEntry("abc/") { }

        when:
        def archiveEntry = transformer.transform(zip)

        then:
        archiveEntry.path == "abc/"
        archiveEntry.crc == -1
        archiveEntry.directory
        archiveEntry.size == -1
    }

    ZipEntry zipEntry(String name, Closure c) {
        def ze = new ZipEntry(name)
        ze.with(c)
        ze
    }
}
