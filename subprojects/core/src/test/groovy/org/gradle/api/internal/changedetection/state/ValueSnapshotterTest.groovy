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

    def "creates snapshot for null value"() {
        expect:
        def snapshot = snapshotter.snapshot(null)
        snapshot.is NullValueSnapshot.INSTANCE
    }

    def "creates snapshot for custom type"() {
        def value = new Bean()

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot instanceof DefaultValueSnapshot
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(new Bean())
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))
    }

    def "creates snapshot for string from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        snapshotter.snapshot("abc",snapshot).is(snapshot)
        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot(null, snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) != snapshot
    }

    def "creates snapshot for null from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(null)
        snapshotter.snapshot(null,snapshot).is(snapshot)
        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) != snapshot
    }

    def "creates snapshot for custom type from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(new Bean(prop: "value"))
        snapshotter.snapshot(new Bean(prop: "value"), snapshot).is(snapshot)
        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) != snapshot
    }

    static class Bean implements Serializable {
        String prop
    }
}
