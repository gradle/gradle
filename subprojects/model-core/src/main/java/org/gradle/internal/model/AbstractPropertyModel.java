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

package org.gradle.internal.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AbstractPropertyModel<T> implements PropertyModel<T> {
    private final String qualifiedName;
    private final T value;

    public AbstractPropertyModel(String qualifiedName, @Nullable T value) {
        this.qualifiedName = qualifiedName;
        this.value = value;
    }

    @Override
    public String getQualifiedName() {
        return qualifiedName;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public int compareTo(@Nonnull PropertyModel<?> o) {
        return qualifiedName.compareTo(o.getQualifiedName());
    }
}
