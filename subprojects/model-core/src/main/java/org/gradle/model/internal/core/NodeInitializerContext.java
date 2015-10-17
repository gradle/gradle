/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core;

import com.google.common.base.Optional;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.type.ModelType;

public class NodeInitializerContext {
    private final ModelType<?> modelType;
    private final Optional<ModelProperty<?>> modelProperty;
    private final Optional<? extends ModelType<?>> declaringType;

    public NodeInitializerContext(ModelType<?> modelType, Optional<ModelProperty<?>> modelProperty, Optional<? extends ModelType<?>> declaringType) {
        this.modelType = modelType;
        this.modelProperty = modelProperty;
        this.declaringType = declaringType;
    }

    public static NodeInitializerContext forType(ModelType<?> modelType) {
        return new NodeInitializerContext(modelType, Optional.<ModelProperty<?>>absent(), Optional.<ModelType<?>>absent());
    }

    public static NodeInitializerContext forProperty(ModelType<?> modelType, ModelProperty<?> modelProperty, ModelType<?> containingType) {
        return new NodeInitializerContext(modelType, Optional.<ModelProperty<?>>of(modelProperty), Optional.of(containingType));
    }

    public ModelType<?> getModelType() {
        return modelType;
    }

    public Optional<ModelProperty<?>> getModelProperty() {
        return modelProperty;
    }

    public Optional<? extends ModelType<?>> getDeclaringType() {
        return declaringType;
    }
}
