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

package org.gradle.api.internal.provider;

import org.gradle.internal.Cast;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nonnull;
import java.util.Objects;

public class DefaultEnumProperty<T> extends DefaultProperty<T> {
    public DefaultEnumProperty(PropertyHost propertyHost, Class<T> type) {
        super(propertyHost, type);
    }

    @Override
    @Nonnull
    public Class<T> getType() {
        return Objects.requireNonNull(super.getType());
    }

    @Override
    public void setFromAnyValue(Object object) {
        if (object instanceof String) {
            // We know the property holds an enum value
            T t = Cast.uncheckedCast(Enum.valueOf(Cast.uncheckedCast(getType()), TextUtil.toUpperCaseLocaleSafe(String.valueOf(object))));
            set(t);
        } else {
            super.setFromAnyValue(object);
        }
    }
}
