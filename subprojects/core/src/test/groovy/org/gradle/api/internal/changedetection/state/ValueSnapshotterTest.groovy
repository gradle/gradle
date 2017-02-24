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

import spock.lang.Specification

class ValueSnapshotterTest extends Specification {
    def snapshotter = new ValueSnapshotter()

    def "creates snapshot for string"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        snapshot == new StringValueSnapshot("abc")
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
        snapshot == new DefaultValueSnapshot(value)
    }

    static class Bean {
    }
}
