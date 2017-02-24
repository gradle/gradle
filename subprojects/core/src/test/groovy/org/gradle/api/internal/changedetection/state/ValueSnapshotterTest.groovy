/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import spock.lang.Specification

class ValueSnapshotterTest extends Specification {
    def snapshotter = new ValueSnapshotter(Stub(ClassLoaderHierarchyHasher))

    def "creates snapshot for string"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        snapshot instanceof StringValueSnapshot
        snapshot == snapshotter.snapshot("abc")
        snapshot != snapshotter.snapshot("other")
    }

    def "creates snapshot for boolean"() {
        expect:
        snapshotter.snapshot(true).is BooleanValueSnapshot.TRUE
        snapshotter.snapshot(false).is BooleanValueSnapshot.FALSE
    }

    def "creates snapshot for null value"() {
        expect:
        snapshotter.snapshot(null).is NullValueSnapshot.INSTANCE
    }

    def "creates snapshot for list"() {
        expect:
        def snapshot1 = snapshotter.snapshot([])
        snapshot1 instanceof ListValueSnapshot
        snapshot1 == snapshotter.snapshot([])
        snapshot1 != snapshotter.snapshot("abc")

        def snapshot2 = snapshotter.snapshot(["123"])
        snapshot2 instanceof ListValueSnapshot
        snapshot2 == snapshotter.snapshot(["123"])
        snapshot2 != snapshot1
    }

    def "creates snapshot for custom type"() {
        def value = new Bean()

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot instanceof SerializedValueSnapshot
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(new Bean())
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))
    }

    def "creates snapshot for string from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        snapshotter.snapshot("abc", snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(null, snapshot) != snapshot
        snapshotter.snapshot(null, snapshot) == snapshotter.snapshot(null)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for null from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(null)
        snapshotter.snapshot(null, snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for boolean from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(true)
        snapshotter.snapshot(true, snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for list from candidate"() {
        expect:
        def snapshot1 = snapshotter.snapshot([])
        snapshotter.snapshot([], snapshot1).is(snapshot1)

        snapshotter.snapshot(["123"], snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1

        def snapshot2 = snapshotter.snapshot(["123"])
        snapshotter.snapshot(["123"], snapshot2).is(snapshot2)

        snapshotter.snapshot(["456"], snapshot2) != snapshot2
        snapshotter.snapshot([], snapshot2) != snapshot2
        snapshotter.snapshot(["123", "456"], snapshot2) != snapshot2

        def snapshot3 = snapshotter.snapshot([new Bean(prop: "value")])
        snapshotter.snapshot([new Bean(prop: "value")], snapshot3).is(snapshot3)

        snapshotter.snapshot([new Bean(prop: "value 2")], snapshot3) != snapshot3
        snapshotter.snapshot([], snapshot3) != snapshot3
        snapshotter.snapshot([new Bean(prop: "value"), new Bean(prop: "value")], snapshot3) != snapshot3

        def snapshot4 = snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value2")])
        snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value2")], snapshot4).is(snapshot4)

        snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value3")], snapshot4) != snapshot4
    }

    def "creates snapshot for serializable type from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(new Bean(prop: "value"))
        snapshotter.snapshot(new Bean(prop: "value"), snapshot).is(snapshot)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")
    }

    static class Bean implements Serializable {
        String prop
    }
}
