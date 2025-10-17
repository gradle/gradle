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

package org.gradle.internal.buildoption;

public abstract class AbstractInternalOption<T> implements InternalOption<T> {

    private static final String INTERNAL_PROPERTY_PREFIX = "org.gradle.internal.";

    private final String systemPropertyName;

    public AbstractInternalOption(String systemPropertyName) {
        if (systemPropertyName == null || !systemPropertyName.startsWith(INTERNAL_PROPERTY_PREFIX)) {
            throw new IllegalArgumentException("Internal property name must start with '" + INTERNAL_PROPERTY_PREFIX + "'");
        }

        this.systemPropertyName = systemPropertyName;
    }

    @Override
    public String getSystemPropertyName() {
        return systemPropertyName;
    }
}
