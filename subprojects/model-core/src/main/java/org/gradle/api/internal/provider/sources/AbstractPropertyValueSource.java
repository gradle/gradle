/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.Describable;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import javax.annotation.Nullable;

public abstract class AbstractPropertyValueSource implements ValueSource<String, GradlePropertyValueSource.Parameters>, Describable {

    public interface Parameters extends ValueSourceParameters {
        Property<String> getPropertyName();
    }

    @Nullable
    @Override
    public abstract String obtain();

    @Override
    public abstract String getDisplayName();

    @Nullable
    protected String propertyNameOrNull() {
        return getParameters().getPropertyName().getOrNull();
    }
}
