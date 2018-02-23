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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import groovy.transform.CompileStatic
import spock.lang.Specification

class ArchiveEntryTest extends Specification {

    def "equals and hash code"() {
        when:
        def props = [
                path     : "foo",
                size     : 10,
                crc      : 10,
                directory: false
        ]

        def a1 = ArchiveEntry.of(props)
        def a2 = ArchiveEntry.of(props)

        then:
        a1 == a2
        a2 == a1
        a1.hashCode() == a2.hashCode()

        when:
        a2 = ArchiveEntry.of(props + [size: 20])

        then:
        a1 != a2
        a2 != a1
        a1.hashCode() != a2.hashCode()

        when:
        def a3 = ArchiveEntry.of(props)
        a1 = ArchiveEntry.of(props + [subEntries: set(a3)])

        then:
        a1 != a2
        a2 != a1
        a1.hashCode() != a2.hashCode()

        when:
        a2 = ArchiveEntry.of(props + [subEntries: set(a3)])

        then:
        a1 == a2
        a2 == a1
        a1.hashCode() == a2.hashCode()

        when:
        def a4 = ArchiveEntry.of(props + [size: 20])
        a1 = ArchiveEntry.of(props + [subEntries: set(a4)])

        then:
        a1 != a2
        a2 != a1
        a1.hashCode() != a2.hashCode()

        when:
        a1 = ArchiveEntry.of(props)

        then:
        a1 != a2
        a2 != a1
        a1.hashCode() != a2.hashCode()

        when:
        a1 = ArchiveEntry.of(props + [crc: 20])

        then:
        a1 != a2
        a2 != a1
        a1.hashCode() != a2.hashCode()
    }

    @CompileStatic // Workaround for https://issues.apache.org/jira/browse/GROOVY-7879 on Java 9
    static <T> ImmutableSet<T> set(T entry) {
        ImmutableSet.of(entry)
    }

    def "path ordering"() {
        expect:
        path("a") == path("a")
        path("a") < path("a", "a")
        path("z") > path("a", "a")
        path("a", "a") < path("a", "b")
    }

    def "paths are case sensitive"() {
        when:
        def a1Props = [
                path     : "foo",
                size     : 10,
                crc      : 10,
                directory: false
        ]
        def a2Props = [
                path     : "Foo",
                size     : 10,
                crc      : 10,
                directory: false
        ]

        def a1 = ArchiveEntry.of(a1Props)
        def a2 = ArchiveEntry.of(a2Props)

        then:
        a1.path != a2.path
    }

    static ArchiveEntry.Path path(String... components) {
        new ArchiveEntry.Path(ImmutableList.copyOf(components))
    }
}
