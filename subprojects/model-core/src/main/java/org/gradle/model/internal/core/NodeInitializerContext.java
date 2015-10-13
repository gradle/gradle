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
import org.gradle.model.internal.type.ModelType;

public class NodeInitializerContext {
    private final ModelType<?> modelType;
    private final Optional<String> propertyName;

    public static NodeInitializerContext forType(ModelType<?> modelType) {
        return new NodeInitializerContext(modelType, Optional.<String>absent());
    }

    public static NodeInitializerContext forProperty(ModelType<?> modelType, String propertyName) {
        return new NodeInitializerContext(modelType, Optional.of(propertyName));
    }

    public NodeInitializerContext(ModelType<?> modelType, Optional<String> propertyName) {
        this.modelType = modelType;
        this.propertyName = propertyName;
    }

    public ModelType<?> getModelType() {
        return modelType;
    }

    public Optional<String> getPropertyName() {
        return propertyName;
    }
}
