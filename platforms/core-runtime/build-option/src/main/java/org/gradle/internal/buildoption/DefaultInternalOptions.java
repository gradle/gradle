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

import java.util.Map;

public class DefaultInternalOptions implements InternalOptions {
    private final Map<String, String> startParameterSystemProperties;

    public DefaultInternalOptions(Map<String, String> startParameterSystemProperties) {
        this.startParameterSystemProperties = startParameterSystemProperties;
    }

    @Override
    public <T> Option.Value<T> getOption(InternalOption<T> option) {
        String value = startParameterSystemProperties.get(option.getSystemPropertyName());
        if (value == null) {
            value = System.getProperty(option.getSystemPropertyName());
        }
        if (value == null) {
            return Option.Value.defaultValue(option.getDefaultValue());
        } else {
            return Option.Value.value(option.convert(value));
        }
    }
}
