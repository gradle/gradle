/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildoption;

import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

public class StringInternalOption<T extends @Nullable String> extends InternalOption<String> {
    private final T defaultValue;

    private StringInternalOption(String propertyName, T defaultValue) {
        super(propertyName);
        this.defaultValue = defaultValue;
    }

    @Override
    public T getDefaultValue() {
        return defaultValue;
    }

    @Override
    public T convert(String value) {
        return Cast.uncheckedNonnullCast(value);
    }

    public static InternalOption<String> of(String propertyName, String defaultValue) {
        return new StringInternalOption<>(propertyName, defaultValue);
    }

    public static InternalOption<@Nullable String> of(String propertyName) {
        return new StringInternalOption<@Nullable String>(propertyName, null);
    }
}
