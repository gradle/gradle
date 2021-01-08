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
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerContext;
import org.gradle.model.internal.inspect.FactoryBasedStructNodeInitializer;
import org.gradle.model.internal.manage.binding.StructBindings;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.schema.ManagedImplSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.typeregistration.InstanceFactory;

import java.util.Set;

public class FactoryBasedStructNodeInitializerExtractionStrategy<T> implements NodeInitializerExtractionStrategy {
    private final InstanceFactory<T> instanceFactory;
    private final StructBindingsStore bindingsStore;

    public FactoryBasedStructNodeInitializerExtractionStrategy(InstanceFactory<T> instanceFactory, StructBindingsStore bindingsStore) {
        this.instanceFactory = instanceFactory;
        this.bindingsStore = bindingsStore;
    }

    @Override
    public <S> NodeInitializer extractNodeInitializer(ModelSchema<S> schema, NodeInitializerContext<S> context) {
        if (!instanceFactory.getBaseInterface().isAssignableFrom(schema.getType())) {
            return null;
        }
        NodeInitializer nodeInitializer = getNodeInitializer(Cast.<ModelSchema<? extends T>>uncheckedCast(schema));
        if (nodeInitializer == null) {
            throw new IllegalArgumentException(String.format("Cannot create an instance of type '%s' as this type is not known. Known types: %s.", schema.getType(), formatKnownTypes(context.getConstraints(), instanceFactory.getSupportedTypes())));
        }
        return nodeInitializer;
    }

    private String formatKnownTypes(Spec<ModelType<?>> constraints, Set<? extends ModelType<?>> supportedTypes) {
        StringBuilder builder = new StringBuilder();
        for (ModelType<?> supportedType : supportedTypes) {
            if (constraints.isSatisfiedBy(supportedType)) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(supportedType);
            }
        }
        if (builder.length() == 0) {
            return "(none)";
        }
        return builder.toString();
    }

    private <S extends T> NodeInitializer getNodeInitializer(final ModelSchema<S> schema) {
        StructSchema<S> publicSchema = Cast.uncheckedCast(schema);
        InstanceFactory.ImplementationInfo implementationInfo = getImplementationInfo(publicSchema);
        if (implementationInfo == null) {
            return null;
        }
        Set<ModelType<?>> internalViews = implementationInfo.getInternalViews();
        ModelType<?> delegateType = implementationInfo.getDelegateType();
        StructBindings<S> bindings = bindingsStore.getBindings(publicSchema.getType(), internalViews, delegateType);
        return new FactoryBasedStructNodeInitializer<T, S>(bindings, implementationInfo);
    }

    private <S extends T> InstanceFactory.ImplementationInfo getImplementationInfo(StructSchema<S> schema) {
        ModelType<S> publicType = schema.getType();
        return schema instanceof ManagedImplSchema
            ? instanceFactory.getManagedSubtypeImplementationInfo(publicType)
            : instanceFactory.getImplementationInfo(publicType);
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableSet.<ModelType<?>>copyOf(instanceFactory.getSupportedTypes());
    }
}
