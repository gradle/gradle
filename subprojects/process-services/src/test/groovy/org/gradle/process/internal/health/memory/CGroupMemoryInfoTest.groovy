/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.process.internal.health.memory

import spock.lang.Specification

class CGroupMemoryInfoTest extends Specification {
    private static final long MB_IN_BYTES = 1024 * 1024 * 1024

    def "parses memory from cgroup values"() {
        def snapshot = new CGroupMemoryInfo().getOsSnapshotFromCgroup(mbsToBytesAsString(800), mbsToBytesAsString(1024))

        expect:
        snapshot.physicalMemory.total == mbsToBytes(1024)
        snapshot.physicalMemory.free == mbsToBytes(224)
    }

    def "negative free memory returns zero"() {
        def snapshot = new CGroupMemoryInfo().getOsSnapshotFromCgroup(mbsToBytesAsString(1024), mbsToBytesAsString(512))

        expect:
        snapshot.physicalMemory.total == mbsToBytes(512)
        snapshot.physicalMemory.free == 0
    }

    def "throws unsupported operation exception when non-numeric values are provided"() {
        when:
        new CGroupMemoryInfo().getOsSnapshotFromCgroup("foo", "bar")

        then:
        thrown(UnsupportedOperationException)
    }

    long mbsToBytes(int mbs) {
        return mbs * MB_IN_BYTES
    }

    String mbsToBytesAsString(int mbs) {
        return mbsToBytes(mbs) as String
    }
}
