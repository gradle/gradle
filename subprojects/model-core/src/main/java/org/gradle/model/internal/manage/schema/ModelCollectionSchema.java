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

import org.gradle.model.ModelMap;
import org.gradle.model.ModelSet;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.type.ModelType;

public class ModelCollectionSchema<T> extends ModelSchema<T> {
    private final ModelType<?> elementType;
    private boolean map;

    public ModelCollectionSchema(ModelType<T> type, ModelType<?> elementType) {
        super(type, Kind.COLLECTION);
        this.elementType = elementType;
        if (type.getRawClass().equals(ModelMap.class)) {
            map = true;
        } else if (type.getRawClass().equals(ModelSet.class) || type.getRawClass().equals(ManagedSet.class)) {
            map = false;
        } else {
            throw new IllegalArgumentException("Expected type of either ModelMap or ModelSet");
        }
    }

    public ModelType<?> getElementType() {
        return elementType;
    }

    public boolean isMap() {
        return map;
    }
}
