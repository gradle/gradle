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

import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.Map;

/**
 * Key-value data published by a test
 *
 * This implementation is intended to be kept within the build process and workers.
 */
@NullMarked
public class DefaultTestKeyValueDataEvent extends AbstractTestDataEvent {
    private final Map<String, String> values;

    public DefaultTestKeyValueDataEvent(Instant logTime, Map<String, String> values) {
        super(logTime);
        this.values = values;
    }

    public Map<String, String> getValues() {
        return values;
    }
}
