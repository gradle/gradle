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

package org.gradle.api.internal.provider.sources;

import org.jspecify.annotations.Nullable;

public abstract class SystemPropertyValueSource extends AbstractPropertyValueSource<SystemPropertyValueSource.Parameters> {

    public interface Parameters extends AbstractPropertyValueSource.Parameters {}

    @Nullable
    @Override
    public String obtain() {
        @Nullable String propertyName = propertyNameOrNull();
        if (propertyName == null) {
            return null;
        }
        return System.getProperty(propertyName);
    }

    @Override
    public String getDisplayName() {
        return String.format("system property '%s'", propertyNameOrNull());
    }
}
