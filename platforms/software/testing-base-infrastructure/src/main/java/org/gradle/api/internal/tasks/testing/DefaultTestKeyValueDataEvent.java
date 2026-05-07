/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.tasks.testing.TestKeyValueDataEvent;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Key-value data published by a test
 *
 * This implementation is intended to be kept within the build process and workers.
 */
@NullMarked
public class DefaultTestKeyValueDataEvent extends AbstractTestDataEvent implements TestKeyValueDataEvent {
    private static final int TO_STRING_VALUE_LIMIT = 10;
    private final Map<String, String> values;

    public DefaultTestKeyValueDataEvent(Instant logTime, Map<String, String> values) {
        super(logTime);
        this.values = values;
    }

    @Override
    public Map<String, String> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "DefaultTestKeyValueDataEvent{" +
            "logTime=" + getLogTime() +
            ", values=" + values.entrySet().stream().limit(TO_STRING_VALUE_LIMIT).map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")) +
            (values.size() > TO_STRING_VALUE_LIMIT ? ", ..." : "") +
            '}';
    }
}
