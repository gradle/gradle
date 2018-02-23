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

package org.gradle.model.internal.inspect;

import org.gradle.api.GradleException;
import org.gradle.model.internal.type.ModelType;

/**
 * Thrown when a managed model has a read-only (immutable) value type. e.g java.lang.String
 */
public class ReadonlyImmutableManagedPropertyException extends GradleException {

    public ReadonlyImmutableManagedPropertyException(ModelType<?> managedModelType, String name, ModelType<?> propertyType) {
        super(toMessage(managedModelType, name, propertyType));
    }

    private static String toMessage(ModelType<?> managedModelType, String name, ModelType<?> propertyType) {
        return String.format("Invalid managed model type '%s': read only property '%s' has non managed type %s, only managed types can be used", managedModelType, name, propertyType);
    }
}

