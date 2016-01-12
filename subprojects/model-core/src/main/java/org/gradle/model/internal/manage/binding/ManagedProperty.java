/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding;

import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

/**
 * A managed property of a struct.
 */
public class ManagedProperty<T> {
    private final String name;
    private final ModelSchema<T> schema;
    private final boolean writable;
    private final boolean declaredAsHavingUnmanagedType;
    private final boolean internal;

    public ManagedProperty(String name, ModelSchema<T> schema, boolean writable, boolean declaredAsHavingUnmanagedType, boolean internal) {
        this.name = name;
        this.schema = schema;
        this.writable = writable;
        this.declaredAsHavingUnmanagedType = declaredAsHavingUnmanagedType;
        this.internal = internal;
    }

    public String getName() {
        return name;
    }

    public ModelType<T> getType() {
        return schema.getType();
    }

    public ModelSchema<T> getSchema() {
        return schema;
    }

    public boolean isInternal() {
        return internal;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isDeclaredAsHavingUnmanagedType() {
        return declaredAsHavingUnmanagedType;
    }
}
