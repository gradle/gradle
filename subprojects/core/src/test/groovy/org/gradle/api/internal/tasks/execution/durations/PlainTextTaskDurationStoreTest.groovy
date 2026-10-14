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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification

import java.time.Duration

class PlainTextTaskDurationStoreTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def file = tmpDir.file("cache/task-durations.txt")
    def store = new PlainTextTaskDurationStore(file)

    def "missing file yields no history"() {
        expect:
        store.read(Path.path(":a")) == null
    }

    def "written entries round-trip through the file"() {
        when:
        store.write([(Path.path(":b:jar")): durations(5), (Path.path(":a")): durations(1, 2, 3)])

        then:
        file.text == """# Gradle task duration history
# format: 1
:a\t1,2,3
:b:jar\t5
"""
        def reloaded = new PlainTextTaskDurationStore(file)
        reloaded.read(Path.path(":a")) == durations(1, 2, 3)
        reloaded.read(Path.path(":b:jar")) == durations(5)
    }

    def "write preserves entries that are not updated"() {
        given:
        file.text = """# Gradle task duration history
# format: 1
:a\t1
:b\t2
"""

        when:
        store.write([(Path.path(":b")): durations(2, 7)])

        then:
        def reloaded = new PlainTextTaskDurationStore(file)
        reloaded.read(Path.path(":a")) == durations(1)
        reloaded.read(Path.path(":b")) == durations(2, 7)
    }

    def "write preserves entries written by another store since this store loaded the file"() {
        given:
        def other = new PlainTextTaskDurationStore(file)
        store.read(Path.path(":a"))
        other.read(Path.path(":b"))

        when:
        other.write([(Path.path(":b")): durations(2)])
        store.write([(Path.path(":a")): durations(1)])

        then:
        def reloaded = new PlainTextTaskDurationStore(file)
        reloaded.read(Path.path(":a")) == durations(1)
        reloaded.read(Path.path(":b")) == durations(2)
    }

    def "discards the whole file when #reason"() {
        given:
        file.text = content

        expect:
        store.read(Path.path(":a")) == null
        store.read(Path.path(":b")) == null

        where:
        reason                 | content
        "header is missing"    | ":a\t1\n"
        "format is unknown"    | "# Gradle task duration history\n# format: 2\n:a\t1\n"
        "tab is missing"       | "# Gradle task duration history\n# format: 1\n:a 1\n:b\t2\n"
        "duration is invalid"  | "# Gradle task duration history\n# format: 1\n:b\t2\n:a\tx\n"
        "duration is negative" | "# Gradle task duration history\n# format: 1\n:b\t2\n:a\t-1\n"
        "durations are empty"  | "# Gradle task duration history\n# format: 1\n:b\t2\n:a\t\n"
        "path is relative"     | "# Gradle task duration history\n# format: 1\n:b\t2\na\t1\n"
    }

    def "corrupt file is replaced on write"() {
        given:
        file.text = "garbage"

        when:
        store.write([(Path.path(":a")): durations(1)])

        then:
        new PlainTextTaskDurationStore(file).read(Path.path(":a")) == durations(1)
    }

    private static TaskDurations durations(long... millis) {
        TaskDurations.of(millis.collect { Duration.ofMillis(it) })
    }
}
