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

import com.google.common.collect.ImmutableSortedMap;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;

@ThreadSafe
public class ExtractedModelSchema<T> implements ModelSchema<T> {

    private final ModelType<T> type;
    private final Iterable<ModelPropertyFactory<?>> propertyFactories;
    private ImmutableSortedMap<String, ModelProperty<?>> properties;

    public ExtractedModelSchema(ModelType<T> type, Iterable<ModelPropertyFactory<?>> propertyFactories) {
        this.type = type;
        this.propertyFactories = propertyFactories;
    }

    public ModelType<T> getType() {
        return type;
    }

    public ImmutableSortedMap<String, ModelProperty<?>> getProperties() {
        return properties;
    }

    public void setProperties(ImmutableSortedMap<String, ModelProperty<?>> properties) {
        this.properties = properties;
    }

    public Iterable<ModelPropertyFactory<?>> getPropertyFactories() {
        return propertyFactories;
    }
}
