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

package org.gradle.api.internal.provider;

import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;

class TypeSanitizingProvider<T> extends AbstractMappingProvider<T, T> {
    private final DisplayName owner;
    private final ValueSanitizer<? super T> sanitizer;
    private final Class<? super T> targetType;

    public TypeSanitizingProvider(DisplayName owner, ValueSanitizer<? super T> sanitizer, Class<? super T> targetType, ProviderInternal<? extends T> delegate) {
        super(Cast.uncheckedNonnullCast(targetType), delegate);
        this.owner = owner;
        this.sanitizer = sanitizer;
        this.targetType = targetType;
    }

    @Override
    protected String getMapDescription() {
        return "check-type";
    }

    @Override
    protected T mapValue(T v) {
        v = Cast.uncheckedCast(sanitizer.sanitize(v));
        if (targetType.isInstance(v)) {
            return v;
        }
        throw new IllegalArgumentException(String.format("Cannot get the value of %s of type %s as the provider associated with this property returned a value of type %s.", owner.getDisplayName(), targetType.getName(), v.getClass().getName()));
    }
}
