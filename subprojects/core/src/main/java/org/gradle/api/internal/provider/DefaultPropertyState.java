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

package org.gradle.api.internal.provider;

import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;

public class DefaultPropertyState<T> implements PropertyState<T> {

    static final String NON_NULL_VALUE_EXCEPTION_MESSAGE = "Needs to set a non-null value before it can be retrieved";
    private T value;

    @Override
    public void set(T value) {
        this.value = value;
    }

    @Override
    public void set(Provider<? extends T> provider) {
        this.value = provider.getOrNull();
    }

    @Internal
    @Override
    public T get() {
        if (value == null) {
            throw new IllegalStateException(NON_NULL_VALUE_EXCEPTION_MESSAGE);
        }

        return value;
    }

    @Internal
    @Override
    public T getOrNull() {
        return value;
    }

    @Override
    public boolean isPresent() {
        return value != null;
    }

    @Override
    public String toString() {
        return String.format("value: %s", value);
    }
}