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

package org.gradle.api.internal.tasks.execution.durations

import org.gradle.util.Path
import spock.lang.Specification

import java.time.Duration

class DefaultTaskDurationHistoryTest extends Specification {
    def store = new InMemoryStore()
    def history = new DefaultTaskDurationHistory(store)

    def "reads durations from the store"() {
        given:
        store.entries[Path.path(":a")] = durations(1, 2)

        expect:
        history.getDurations(Path.path(":a")) == durations(1, 2)
        history.getDurations(Path.path(":b")) == null
    }

    def "recorded samples are not visible until persisted"() {
        when:
        history.record(Path.path(":a"), Duration.ofMillis(5))

        then:
        history.getDurations(Path.path(":a")) == null
    }

    def "stop merges recorded samples into stored entries and writes only changed entries"() {
        given:
        store.entries[Path.path(":a")] = durations(1, 2, 3)
        store.entries[Path.path(":untouched")] = durations(9)

        when:
        history.record(Path.path(":a"), Duration.ofMillis(4))
        history.record(Path.path(":b"), Duration.ofMillis(7))
        history.stop()

        then:
        store.writes == [[(Path.path(":a")): durations(2, 3, 4), (Path.path(":b")): durations(7)]]
    }

    def "stop does not write when nothing was recorded"() {
        when:
        history.stop()

        then:
        store.writes.empty
    }

    private static TaskDurations durations(long... millis) {
        TaskDurations.of(millis.collect { Duration.ofMillis(it) })
    }

    private static class InMemoryStore implements TaskDurationStore {
        final Map<Path, TaskDurations> entries = [:]
        final List<Map<Path, TaskDurations>> writes = []

        @Override
        TaskDurations read(Path buildTreeTaskPath) {
            entries[buildTreeTaskPath]
        }

        @Override
        void write(Map<Path, TaskDurations> updated) {
            writes << new HashMap<>(updated)
            entries.putAll(updated)
        }
    }
}
