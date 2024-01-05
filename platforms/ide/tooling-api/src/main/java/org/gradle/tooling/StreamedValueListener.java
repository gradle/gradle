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

package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * Receives a value sent via {@link BuildController#send(Object)}.
 *
 * <p>Objects are received in the order they were sent and all objects are received before the result of the {@link BuildAction} is returned to the application.</p>
 *
 * @since 8.6
 */
@Incubating
public interface StreamedValueListener {
    /**
     * Handles the next value.
     *
     * @since 8.6
     */
    void onValue(Object value);
}
