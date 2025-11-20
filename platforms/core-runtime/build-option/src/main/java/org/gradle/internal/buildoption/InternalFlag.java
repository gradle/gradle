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

/**
 * A boolean internal option.
 */
public class InternalFlag extends InternalOption<Boolean> {
    private final boolean defaultValue;

    public InternalFlag(String propertyName) {
        this(propertyName, false);
    }

    public InternalFlag(String propertyName, boolean defaultValue) {
        super(propertyName);
        this.defaultValue = defaultValue;
    }

    @Override
    public Boolean getDefaultValue() {
        return defaultValue;
    }

    @Override
    public Boolean convert(String value) {
        return BooleanOptionUtil.isTrue(value);
    }
}
