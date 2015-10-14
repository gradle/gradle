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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils;
import org.gradle.model.internal.type.ModelType;

import java.util.*;

public class BaseInstanceFactory<T> implements InstanceFactory<T> {

    private class FactoryRegistration<S extends T> {
        private final ModelRuleDescriptor source;
        private final BiFunction<? extends S, String, ? super MutableModelNode> factory;

        public FactoryRegistration(@Nullable ModelRuleDescriptor source, BiFunction<? extends S, String, ? super MutableModelNode> factory) {
            this.source = source;
            this.factory = factory;
        }
    }

    private class ImplementationTypeRegistration<S extends T> {
        private final ModelRuleDescriptor source;
        private final ModelType<? extends S> implementationType;

        private ImplementationTypeRegistration(@Nullable ModelRuleDescriptor source, ModelType<? extends S> implementationType) {
            this.source = source;
            this.implementationType = implementationType;
        }
    }

    private class InternalViewRegistration {
        private final ModelRuleDescriptor source;
        private final ModelType<?> internalView;

        private InternalViewRegistration(@Nullable ModelRuleDescriptor source, ModelType<?> internalView) {
            this.source = source;
            this.internalView = internalView;
        }
    }

    private final String displayName;
    private final ModelType<T> baseType;
    private final Set<ModelType<? extends T>> publicTypes = Sets.newLinkedHashSet();
    private final Map<Class<? extends T>, FactoryRegistration<? extends T>> factories = Maps.newIdentityHashMap();
    private final Map<Class<? extends T>, ImplementationTypeRegistration<? extends T>> implementationTypes = Maps.newIdentityHashMap();
    private final Map<Class<? extends T>, List<InternalViewRegistration>> internalViews = Maps.newIdentityHashMap();

    public BaseInstanceFactory(String displayName, Class<T> baseType) {
        this.displayName = displayName;
        this.baseType = ModelType.of(baseType);
    }

    @Override
    public ModelType<T> getBaseType() {
        return baseType;
    }

    @Override
    public void registerPublicType(ModelType<? extends T> type) {
        publicTypes.add(type);
    }

    @Override
    public <S extends T> void registerFactory(ModelType<S> type, @Nullable ModelRuleDescriptor sourceRule, BiFunction<? extends S, String, ? super MutableModelNode> factory) {
        FactoryRegistration<S> factoryRegistration = getFactoryRegistration(type);
        if (factoryRegistration != null) {
            throw new GradleException(getDuplicateRegistrationMessage("a factory", type, factoryRegistration.source));
        }
        factories.put(type.getConcreteClass(), new FactoryRegistration<S>(sourceRule, factory));
    }

    @Override
    public <S extends T, I extends S> void registerImplementation(ModelType<S> type, @Nullable ModelRuleDescriptor sourceRule, ModelType<I> implementationType) {
        ImplementationTypeRegistration<S> implementationTypeRegistration = getImplementationTypeRegistration(type);
        if (implementationTypeRegistration != null) {
            throw new GradleException(getDuplicateRegistrationMessage("an implementation type", type, implementationTypeRegistration.source));
        }
        implementationTypes.put(type.getConcreteClass(), new ImplementationTypeRegistration<S>(sourceRule, implementationType));
    }

    @Override
    public <S extends T> DelegationInfo<? extends T> getDelegationInfo(ModelType<S> type) {
        final Set<DelegationInfo<? extends T>> delegates = Sets.newLinkedHashSet();
        ModelSchemaUtils.walkTypeHierarchy(type.getConcreteClass(), new ModelSchemaUtils.TypeVisitor<S>() {
            @Override
            public void visitType(Class<? super S> superTypeClass) {
                ModelType<? super S> superType = ModelType.of(superTypeClass);
                ModelType<? extends T> superTypeAsBaseType = baseType.asSubclass(superType);
                if (superTypeAsBaseType == null) {
                    return;
                }
                ImplementationTypeRegistration<? extends T> registration = implementationTypes.get(superTypeAsBaseType.getConcreteClass());
                if (registration != null) {
                    ModelType<? extends T> implementationType = registration.implementationType;
                    delegates.add(new DelegationInfo<T>(superTypeAsBaseType, implementationType));
                }
            }
        });
        switch (delegates.size()) {
            case 0:
                return null;
            case 1:
                return delegates.iterator().next();
            default:
                throw new IllegalStateException(String.format("More than one implementation type registered for %s, super-types that registered an implementation are: %s",
                    type,
                    Joiner.on(", ").join(delegates)));
        }
    }

    @Override
    public <S extends T> void registerInternalView(ModelType<S> type, @Nullable ModelRuleDescriptor sourceRule, ModelType<?> internalViewType) {
        List<InternalViewRegistration> internalViewRegistrations = getInternalViewRegistrations(type);
        if (internalViewRegistrations == null) {
            internalViewRegistrations = Lists.newArrayList();
            internalViews.put(type.getConcreteClass(), internalViewRegistrations);
        }
        internalViewRegistrations.add(new InternalViewRegistration(sourceRule, internalViewType));
    }

    private <S extends T> String getDuplicateRegistrationMessage(String entity, ModelType<S> publicType, @Nullable ModelRuleDescriptor source) {
        StringBuilder builder = new StringBuilder(String.format("Cannot register %s for type %s because a factory for this type was already registered",
            entity, publicType.getSimpleName()));

        if (source != null) {
            builder.append(" by ");
            source.describeTo(builder);
        }
        builder.append(".");
        return builder.toString();
    }

    private <S extends T> FactoryRegistration<S> getFactoryRegistration(ModelType<S> type) {
        return Cast.uncheckedCast(factories.get(type.getConcreteClass()));
    }

    private <S extends T> List<InternalViewRegistration> getInternalViewRegistrations(ModelType<S> type) {
        return internalViews.get(type.getConcreteClass());
    }

    private <S extends T> ImplementationTypeRegistration<S> getImplementationTypeRegistration(ModelType<S> type) {
        return Cast.uncheckedCast(implementationTypes.get(type.getConcreteClass()));
    }

    @Override
    public <S extends T> S create(ModelType<S> type, MutableModelNode modelNode, String name) {
        FactoryRegistration<S> factoryRegistration = getFactoryRegistration(type);
        if (factoryRegistration == null) {
            throw new IllegalArgumentException(
                String.format("Cannot create a %s because this type is not known to %s. Known types are: %s", type.getSimpleName(), displayName, getSupportedTypeNames()));
        }
        return factoryRegistration.factory.apply(name, modelNode);
    }

    @Override
    public Set<ModelType<? extends T>> getSupportedTypes() {
        return ImmutableSet.copyOf(publicTypes);
    }

    private String getSupportedTypeNames() {
        List<String> names = Lists.newArrayList();
        for (Class<?> clazz : factories.keySet()) {
            names.add(clazz.getSimpleName());
        }
        Collections.sort(names);
        return names.isEmpty() ? "(None)" : Joiner.on(", ").join(names);
    }

    @Override
    public <S extends T> Set<ModelType<?>> getInternalViews(ModelType<S> type) {
        List<InternalViewRegistration> internalViewRegistrations = internalViews.get(type.getConcreteClass());
        if (internalViewRegistrations == null) {
            return ImmutableSet.of();
        }
        return ImmutableSet.copyOf(Iterables.transform(internalViewRegistrations, new Function<InternalViewRegistration, ModelType<?>>() {
            @Override
            public ModelType<?> apply(InternalViewRegistration registration) {
                return registration.internalView;
            }
        }));
    }

    @Override
    public void validateRegistrations() {
        for (Class<? extends T> type : implementationTypes.keySet()) {
            validateRegistration(ModelType.of(type));
        }
    }

    private <S extends T> void validateRegistration(ModelType<S> publicType) {
        ImplementationTypeRegistration<S> implementationTypeRegistration = getImplementationTypeRegistration(publicType);
        List<InternalViewRegistration> internalViewRegistrations = getInternalViewRegistrations(publicType);
        if (internalViewRegistrations == null) {
            // No internal views are registered for this type
            return;
        }
        ModelType<? extends S> implementation = implementationTypeRegistration.implementationType;
        for (InternalViewRegistration internalViewRegistration : internalViewRegistrations) {
            ModelType<?> internalView = internalViewRegistration.internalView;
            ModelType<?> asSubclass = internalView.asSubclass(implementation);
            if (asSubclass == null) {
                StringBuilder builder = new StringBuilder();

                builder.append(String.format("Factory registration for '%s' is invalid because the implementation type '%s' does not extend internal view '%s'",
                    publicType.getSimpleName(), implementation, internalView));

                if (implementationTypeRegistration.source != null) {
                    builder.append(". Implementation type was registered by ");
                    implementationTypeRegistration.source.describeTo(builder);
                }
                if (internalViewRegistration.source != null) {
                    builder.append(". Internal view was registered by ");
                    internalViewRegistration.source.describeTo(builder);
                }
                builder.append(".");
                throw new GradleException(builder.toString());
            }
        }
    }

    @Override
    public String toString() {
        return "[" + getSupportedTypeNames() + "]";
    }
}
