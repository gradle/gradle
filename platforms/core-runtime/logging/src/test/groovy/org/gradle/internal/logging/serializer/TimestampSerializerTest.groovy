/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.logging.serializer

import org.gradle.internal.time.Timestamp

class TimestampSerializerTest extends LogSerializerSpec {
    TimestampSerializer serializer = new TimestampSerializer()

    def "can serialize timestamps without losing precision"() {
        def origin = Timestamp.ofMillis(epochMs, nanoAdjustment)

        when:
        def ts = serialize(origin, serializer)

        then:
        ts == origin

        ts.timeMs == epochMs
        ts.nanosOfMillis == nanoAdjustment

        where:
        epochMs        | nanoAdjustment
        0              | 0
        0              | 1
        0              | 1_001
        0              | 999_999
        1_000          | 0
        1_000          | 1
        1_000          | 1_001
        1_000          | 999_999
        4_000_000_000L | 0
        4_000_000_000L | 1
        4_000_000_000L | 1_001
        4_000_000_000L | 999_999
    }
}
