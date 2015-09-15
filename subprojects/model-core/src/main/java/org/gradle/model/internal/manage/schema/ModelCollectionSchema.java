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

import org.gradle.model.internal.core.ModelProjection;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.inspect.ProjectionOnlyNodeInitializer;
import org.gradle.model.internal.type.ModelType;

public class ModelCollectionSchema<T, E> extends AbstractModelSchema<T> implements ManagedImplModelSchema<T> {

    private final ModelType<E> elementType;
    private final NodeInitializer nodeInitializer;

    public ModelCollectionSchema(ModelType<T> type, ModelType<E> elementType, ModelProjection projection) {
        super(type);
        this.elementType = elementType;
        this.nodeInitializer = new ProjectionOnlyNodeInitializer(projection);
    }

    public ModelType<E> getElementType() {
        return elementType;
    }

    @Override
    public NodeInitializer getNodeInitializer() {
        return nodeInitializer;
    }

    @Override
    public String toString() {
        return "collection " + getType();
    }
}
