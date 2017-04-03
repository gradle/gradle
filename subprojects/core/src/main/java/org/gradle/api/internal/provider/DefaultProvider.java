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

import org.gradle.api.provider.Provider;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.Callable;

public class DefaultProvider<T> implements Provider<T> {

    public static final String NON_NULL_VALUE_EXCEPTION_MESSAGE = "Needs to set a non-null value before it can be retrieved";
    private final Callable<T> value;

    public DefaultProvider(Callable<T> value) {
        assert value != null : "value cannot be null";
        this.value = value;
    }

    @Override
    public T get() {
        T evaluatedValue = getOrNull();

        if (evaluatedValue == null) {
            throw new IllegalStateException(NON_NULL_VALUE_EXCEPTION_MESSAGE);
        }

        return evaluatedValue;
    }

    @Override
    public T getOrNull() {
        try {
            return value.call();
        } catch (Exception e) {
            throw new UncheckedException(e);
        }
    }

    @Override
    public boolean isPresent() {
        return getOrNull() != null;
    }

    @Override
    public String toString() {
        return String.format("value: %s", getOrNull());
    }
}
