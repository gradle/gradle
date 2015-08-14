/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.type.ModelType;

@ThreadSafe
public abstract class AbstractModelSchema<T> implements ModelSchema<T> {

    private final ModelType<T> type;
    private final Kind kind;

    protected AbstractModelSchema(ModelType<T> type, Kind kind) {
        this.type = type;
        this.kind = kind;
    }

    @Override
    // intended to be overridden
    public NodeInitializer getNodeInitializer() {
        throw new UnsupportedOperationException("Don't know how to create model element from schema for " + type);
    }

    @Override
    public ModelType<T> getType() {
        return type;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return kind.toString().toLowerCase() + " " + type;
    }
}
