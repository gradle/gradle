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


import org.gradle.api.internal.tasks.testing.DefaultTestKeyValueDataEvent
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification

import java.time.Instant

/**
 * Tests for {@link SerializableTestResult}.
 */
final class SerializableTestResultTest extends Specification {
    def "can serialize test result with metadata"() {
        when:
        def builder = SerializableTestResult.builder()
        builder.name("test")
        builder.displayName("Test 1")
        builder.startTime(Instant.now().toEpochMilli())

        def metadataTime = Instant.now()
        builder.addMetadata(new DefaultTestKeyValueDataEvent(metadataTime, Collections.singletonMap("key", "value")))

        builder.resultType(TestResult.ResultType.SUCCESS)
        builder.endTime(Instant.now().toEpochMilli())
        def result = builder.build()

        then:
        result.metadatas.size() == 1
        result.metadatas[0].logTime == metadataTime
        result.metadatas[0].values.size() == 1
        result.metadatas[0].values["key"] == "value"

    }
}
