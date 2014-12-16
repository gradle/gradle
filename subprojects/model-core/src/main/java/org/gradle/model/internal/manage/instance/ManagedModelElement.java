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

package org.gradle.model.internal.manage.instance;

import com.google.common.collect.ImmutableSortedMap;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

@ThreadSafe
public class ManagedModelElement<T> {

    private final ImmutableSortedMap<String, ModelPropertyInstance<?>> properties;
    private final ModelSchema<T> schema;
    private final ModelSchemaStore schemaStore;
    private final ModelInstantiator instantiator;

    public ManagedModelElement(ModelSchema<T> schema, ModelSchemaStore schemaStore, ModelInstantiator instantiator) {
        this.schema = schema;
        this.schemaStore = schemaStore;
        this.instantiator = instantiator;

        ImmutableSortedMap.Builder<String, ModelPropertyInstance<?>> builder = ImmutableSortedMap.naturalOrder();
        for (ModelProperty<?> property : schema.getProperties().values()) {
            builder.put(property.getName(), ModelPropertyInstance.of(property));
        }
        this.properties = builder.build();
    }

    public ModelType<T> getType() {
        return schema.getType();
    }

    public <U> ModelPropertyInstance<U> get(ModelType<U> propertyType, String propertyName) {
        ModelPropertyInstance<?> modelPropertyInstance = properties.get(propertyName);
        ModelType<?> modelPropertyType = modelPropertyInstance.getMeta().getType();
        if (!modelPropertyType.equals(propertyType)) {
            throw new UnexpectedModelPropertyTypeException(propertyName, schema.getType(), propertyType, modelPropertyType);
        }
        return Cast.uncheckedCast(modelPropertyInstance);
    }

    public ImmutableSortedMap<String, ModelPropertyInstance<?>> getProperties() {
        return properties;
    }

    public ModelElementState getState() {
        return new ModelElementState() {
            public <P> P get(ModelType<P> type, String name) {
                ModelPropertyInstance<P> propertyInstance = ManagedModelElement.this.get(type, name);
                P value = propertyInstance.get();
                if (value == null && !propertyInstance.getMeta().isWritable()) {
                    value = instantiator.newInstance(schemaStore.getSchema(type));
                    propertyInstance.set(value);
                }

                return value;
            }

            public <P> void set(ModelType<P> propertyType, String name, P value) {
                ModelPropertyInstance<P> modelPropertyInstance = ManagedModelElement.this.get(propertyType, name);
                ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);
                if (propertySchema.getKind().isManaged() && !ManagedInstance.class.isInstance(value)) {
                    throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", name, getType()));
                }
                modelPropertyInstance.set(Cast.cast(propertyType.getConcreteClass(), value));
            }
        };
    }
}
