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

package org.gradle.api.data;

/**
 * A shared data key with no namespace for a value with a given {@code type}.
 *
 * Only considered equal to another key with no namespace and the exact
 * same {@code type}.
 *
 * @since 8.7
 */
class NamespacelessDataKey<T> implements SharedDataKey<T> {
    private final Class<T> type;

    public NamespacelessDataKey(Class<T> type) {
        this.type = type;
    }
}
