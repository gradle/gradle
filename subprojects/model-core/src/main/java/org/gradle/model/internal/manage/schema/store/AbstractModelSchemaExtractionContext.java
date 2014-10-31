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

package org.gradle.model.internal.manage.schema.store;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelType;

@ThreadSafe
public abstract class AbstractModelSchemaExtractionContext implements ModelSchemaExtractionContext {

    protected final ModelType<?> owner;

    protected final ModelType<?> type;

    private final ModelSchemaExtractionContext parent;

    protected abstract String getPathFragment();

    AbstractModelSchemaExtractionContext(ModelType<?> owner, ModelType<?> type, ModelSchemaExtractionContext parent) {
        this.owner = owner;
        this.type = type;
        this.parent = parent;
    }

    public ModelType<?> getType() {
        return type;
    }

    public String getContextPath() {
        return parent != null ? String.format("%s -> %s", parent.getContextPath(), getPathFragment()) : getPathFragment();
    }
}
