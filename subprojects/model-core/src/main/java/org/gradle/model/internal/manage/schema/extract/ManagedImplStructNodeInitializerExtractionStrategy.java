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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.Cast;
import org.gradle.model.Managed;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.inspect.ManagedModelInitializer;
import org.gradle.model.internal.manage.schema.ManagedImplStructSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

public class ManagedImplStructNodeInitializerExtractionStrategy implements NodeInitializerExtractionStrategy {

    protected boolean isTarget(ModelType<?> type) {
        return type.getRawClass().isAnnotationPresent(Managed.class);
    }

    @Override
    public <T> NodeInitializer extractNodeInitializer(ModelSchema<T> schema) {
        if (!(schema instanceof ManagedImplStructSchema)) {
            return null;
        }
        if (!isTarget(schema.getType())) {
            return null;
        }
        ManagedImplStructSchema<T> managedSchema = Cast.<ManagedImplStructSchema<T>>uncheckedCast(schema);
        return new ManagedModelInitializer<T>(managedSchema);
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableSet.of();
    }
}
