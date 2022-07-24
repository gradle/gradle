/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language;

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;

/**
 * Represents a binary that is created and configured as required.
 *
 * @since 4.5
 * @param <T> The type of binary.
 */
public interface BinaryProvider<T> extends Provider<T> {
    /**
     * Registers an action to execute to configure the binary. The action is executed only when the element is required.
     */
    void configure(Action<? super T> action);

    /**
     * Registers an action to execute when the binary has been configured. The action is executed only when the element is required.
     *
     * @since 4.6
     */
    void whenFinalized(Action<? super T> action);
}
