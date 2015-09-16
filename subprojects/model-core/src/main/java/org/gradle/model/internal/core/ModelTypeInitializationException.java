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

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.TypeCollectionDescriptor;

import java.util.List;

/**
 * Thrown when a NodeInitializer can not be found for a given type or when the type is not managed and can not be constructed.
 */
@Incubating
public class ModelTypeInitializationException extends GradleException {

    public ModelTypeInitializationException(ModelType<?> type, List<ModelType<?>> candidates) {
        super(toMessage(type, candidates));
    }

    private static String toMessage(ModelType<?> type, List<ModelType<?>> types) {
        TypeCollectionDescriptor supportedTypesDescriptor = new TypeCollectionDescriptor(types);
        return String.format("The model node of type: '%s' can not be constructed. The type must be managed (@Managed) or one of the following types [%s]", type, supportedTypesDescriptor.toString());
    }
}

