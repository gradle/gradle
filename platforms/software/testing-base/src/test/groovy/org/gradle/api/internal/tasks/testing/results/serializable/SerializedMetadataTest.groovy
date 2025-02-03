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

package org.gradle.api.internal.tasks.testing.results.serializable

import spock.lang.Specification

import java.time.Instant

/**
 * Tests for {@link SerializedMetadata}.
 */
final class SerializedMetadataTest extends Specification implements SerializesMetadata {
    private now = Instant.now().toEpochMilli()

    def "can create SerializableMetadata from single entry with String value"() {
        when:
        def metadata = new SerializedMetadata(now, Collections.singletonMap("key", "value"))

        then:
        metadata.logTime == now
        metadata.getEntries().size() == 1
        metadata.getEntries()[0].key == "key"
        metadata.getEntries()[0].value == "value"
        metadata.getEntries()[0].serializedValue == serialize("value")
        metadata.getEntries()[0].valueType == String.class.name
    }

    def "can create SerializableMetadata from single entry with int value"() {
        when:
        def metadata = new SerializedMetadata(now, Collections.singletonMap("key", 1))

        then:
        metadata.logTime == now
        metadata.getEntries().size() == 1
        metadata.getEntries()[0].key == "key"
        metadata.getEntries()[0].value == 1
        metadata.getEntries()[0].serializedValue == serialize(1)
        metadata.getEntries()[0].valueType == Integer.class.name
    }

    def "can create SerializableMetadata from multiple entry map"() {
        when:
        def entries = [
            "key1": "value1",
            "key2": "value2"
        ]
        def metadata = new SerializedMetadata(now, entries)

        then:
        metadata.logTime == now
        metadata.getEntries().size() == 2
        metadata.getEntries()[0].key == "key1"
        metadata.getEntries()[0].value == "value1"
        metadata.getEntries()[0].serializedValue == serialize("value1")
        metadata.getEntries()[0].valueType == String.class.name
        metadata.getEntries()[1].key == "key2"
        metadata.getEntries()[1].value == "value2"
        metadata.getEntries()[1].serializedValue == serialize("value2")
        metadata.getEntries()[1].valueType == String.class.name
    }
}
