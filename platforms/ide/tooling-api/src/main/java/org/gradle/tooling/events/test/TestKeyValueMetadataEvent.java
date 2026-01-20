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

package org.gradle.tooling.events.test;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * An event emitted by tests that contains additional data in the form of key-values.
 * @since 9.4.0
 */
@Incubating
@NullMarked
public interface TestKeyValueMetadataEvent extends TestMetadataEvent {
    /**
     * Key-value data reported by the associated test.
     *
     * @return map of key-values
     * @since 9.4.0
     */
    Map<String, String> getValues();
}
