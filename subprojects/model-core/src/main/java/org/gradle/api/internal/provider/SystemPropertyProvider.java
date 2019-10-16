/*
 * Copyright 2019 the original author or authors.
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

import javax.annotation.Nullable;

public class SystemPropertyProvider extends AbstractReadOnlyProvider<String> {

    private final String propertyName;

    public SystemPropertyProvider(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Nullable
    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Nullable
    @Override
    public String getOrNull() {
        return System.getProperty(propertyName);
    }

    @Override
    public boolean isValueProducedByTask() {
        return true;
    }

    @Override
    public boolean isContentProducedByTask() {
        return true;
    }
}
