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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.internal.Cast;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.Managed;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseInstanceFactory<T> implements InstanceFactory<T> {

    private final String displayName;
    private final ModelType<T> baseInterface;
    private final ModelType<? extends T> baseImplementation;
    private final Map<ModelType<? extends T>, TypeRegistration<? extends T>> registrations = Maps.newLinkedHashMap();

    public BaseInstanceFactory(String displayName, Class<T> baseInterface, Class<? extends T> baseImplementation) {
        this.displayName = displayName;
        this.baseInterface = ModelType.of(baseInterface);
        this.baseImplementation = ModelType.of(baseImplementation);
    }

    @Override
    public ModelType<T> getBaseInterface() {
        return baseInterface;
    }

    @Override
    public <S extends T> TypeRegistrationBuilder<S> register(ModelType<S> publicType, ModelRuleDescriptor source) {
        TypeRegistration<S> registration = Cast.uncheckedCast(registrations.get(publicType));
        if (registration == null) {
            registration = new TypeRegistration<S>(source, publicType);
            registrations.put(publicType, registration);
        }
        return new TypeRegistrationBuilderImpl<S>(source, registration);
    }

    @Override
    public <S extends T> ImplementationInfo<? extends T> getImplementationInfo(ModelType<S> type) {
        final List<ImplementationInfo<? extends T>> implementationInfos = Lists.newArrayListWithCapacity(1);
        ModelSchemaUtils.walkTypeHierarchy(type.getConcreteClass(), new ModelSchemaUtils.TypeVisitor<S>() {
            @Override
            public void visitType(Class<? super S> superTypeClass) {
                if (!baseInterface.getConcreteClass().isAssignableFrom(superTypeClass)) {
                    return;
                }
                Class<? extends T> superTypeClassAsBaseType = superTypeClass.asSubclass(baseInterface.getConcreteClass());
                ModelType<? extends T> superTypeAsBaseType = ModelType.of(superTypeClassAsBaseType);

                TypeRegistration<? extends T> registration = getRegistration(superTypeAsBaseType);
                if (registration != null && registration.implementationRegistration != null) {
                    ModelType<? extends T> implementationType = registration.implementationRegistration.getImplementationType();
                    implementationInfos.add(new ImplementationInfoImpl<T>(superTypeAsBaseType, implementationType));
                }
            }
        });
        switch (implementationInfos.size()) {
            case 1:
                return implementationInfos.get(0);
            case 0:
                throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because it doesn't extend an interface with a default implementation", type));
            default:
                throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because it has multiple default implementations registered, super-types that registered an implementation are: %s",
                    type,
                    Joiner.on(", ").join(implementationInfos)));
        }
    }

    @Override
    public <S extends T> S create(ModelType<S> type, MutableModelNode modelNode, String name) {
        TypeRegistration<S> registration = getRegistration(type);
        if (registration == null || registration.implementationRegistration == null) {
            throw new IllegalArgumentException(
                String.format("Cannot create a '%s' because this type is not known to %s. Known types are: %s", type, displayName, getSupportedTypeNames()));
        }
        BiFunction<? extends S, String, ? super MutableModelNode> factory = registration.implementationRegistration.getFactory();
        return factory.apply(name, modelNode);
    }

    @Override
    public Set<ModelType<? extends T>> getSupportedTypes() {
        return ImmutableSet.copyOf(registrations.keySet());
    }

    private String getSupportedTypeNames() {
        List<String> names = Lists.newArrayList();
        for (ModelType<?> type : registrations.keySet()) {
            names.add(type.toString());
        }
        Collections.sort(names);
        return names.isEmpty() ? "(None)" : Joiner.on(", ").join(names);
    }

    @Override
    public <S extends T> Set<ModelType<?>> getInternalViews(ModelType<S> type) {
        TypeRegistration<S> registration = getRegistration(type);
        if (registration == null) {
            return ImmutableSet.of();
        }
        return ImmutableSet.copyOf(Iterables.transform(registration.internalViewRegistrations, new Function<InternalViewRegistration, ModelType<?>>() {
            @Override
            public ModelType<?> apply(InternalViewRegistration registration) {
                return registration.getInternalView();
            }
        }));
    }

    private <S extends T> TypeRegistration<S> getRegistration(ModelType<S> type) {
        return Cast.uncheckedCast(registrations.get(type));
    }

    @Override
    public void validateRegistrations() {
        for (TypeRegistration<? extends T> registration : registrations.values()) {
            validateRegistration(registration);
        }
    }

    private <S extends T> void validateRegistration(TypeRegistration<S> registration) {
        ModelType<S> publicType = registration.publicType;

        ImplementationRegistration<S> implementationRegistration = registration.implementationRegistration;
        if (implementationRegistration == null) {
            ImplementationInfo<? extends T> implementationInfo = getImplementationInfo(publicType);
            if (!publicType.getConcreteClass().isAnnotationPresent(Managed.class)
                && !publicType.equals(implementationInfo.getPublicType())) {
                throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because only managed types can extend unmanaged type '%s'", publicType, implementationInfo.getPublicType()));
            }
            return;
        }

        List<InternalViewRegistration> internalViewRegistrations = registration.internalViewRegistrations;
        if (internalViewRegistrations == null) {
            // No internal views are registered for this type
            return;
        }
        ModelType<? extends S> implementation = implementationRegistration.getImplementationType();
        for (InternalViewRegistration internalViewRegistration : internalViewRegistrations) {
            ModelType<?> internalView = internalViewRegistration.getInternalView();
            ModelType<?> asSubclass = internalView.asSubclass(implementation);
            if (asSubclass == null) {
                throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because the implementation type '%s' does not extend internal view '%s', "
                        + "implementation type was registered by %s, "
                        + "internal view was registered by %s",
                    publicType, implementation, internalView,
                    implementationRegistration.getSource(),
                    internalViewRegistration.getSource()));
            }
        }
    }

    @Override
    public String toString() {
        return "[" + getSupportedTypeNames() + "]";
    }

    private class TypeRegistrationBuilderImpl<S extends T> implements TypeRegistrationBuilder<S> {

        private final ModelRuleDescriptor source;
        private final TypeRegistration<S> registration;

        public TypeRegistrationBuilderImpl(ModelRuleDescriptor source, TypeRegistration<S> registration) {
            this.source = source;
            this.registration = registration;
        }

        @Override
        public TypeRegistrationBuilder<S> withImplementation(ModelType<? extends S> implementationType, BiFunction<? extends S, String, ? super MutableModelNode> factory) {
            registration.setImplementation(implementationType, source, factory);
            return this;
        }

        @Override
        public TypeRegistrationBuilder<S> withInternalView(ModelType<?> internalView) {
            registration.addInternalView(internalView, source);
            return this;
        }
    }

    private class TypeRegistration<S extends T> {
        private final ModelRuleDescriptor source;
        private final ModelType<S> publicType;
        private ImplementationRegistration<S> implementationRegistration;
        private final List<InternalViewRegistration> internalViewRegistrations = Lists.newArrayList();

        public TypeRegistration(ModelRuleDescriptor source, ModelType<S> publicType) {
            this.source = source;
            this.publicType = publicType;
        }

        public void setImplementation(ModelType<? extends S> implementationType, ModelRuleDescriptor source, BiFunction<? extends S, String, ? super MutableModelNode> factory) {
            if (implementationRegistration != null) {
                throw new IllegalStateException(String.format("Cannot register implementation for type '%s' because an implementation for this type was already registered by %s",
                    publicType, implementationRegistration.getSource()));
            }
            if (!baseInterface.isAssignableFrom(implementationType)) {
                throw new IllegalArgumentException(String.format("Implementation type '%s' registered for '%s' must extend '%s'", implementationType, publicType, baseImplementation));
            }
            if (Modifier.isAbstract(implementationType.getConcreteClass().getModifiers())) {
                throw new IllegalArgumentException(String.format("Implementation type '%s' registered for '%s' must not be abstract", implementationType, publicType));
            }
            try {
                implementationType.getConcreteClass().getConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(String.format("Implementation type '%s' registered for '%s' must have a public default constructor", implementationType, publicType));
            }
            this.implementationRegistration = new ImplementationRegistration<S>(source, implementationType, factory);
        }

        public void addInternalView(ModelType<?> internalView, ModelRuleDescriptor source) {
            internalViewRegistrations.add(new InternalViewRegistration(source, internalView));
        }

        public ModelRuleDescriptor getSource() {
            return source;
        }
    }

    private static class ImplementationRegistration<S> {
        private final ModelRuleDescriptor source;
        private final ModelType<? extends S> implementationType;
        private final BiFunction<? extends S, String, ? super MutableModelNode> factory;

        private ImplementationRegistration(ModelRuleDescriptor source, ModelType<? extends S> implementationType, BiFunction<? extends S, String, ? super MutableModelNode> factory) {
            this.source = source;
            this.implementationType = implementationType;
            this.factory = factory;
        }

        public ModelRuleDescriptor getSource() {
            return source;
        }

        public ModelType<? extends S> getImplementationType() {
            return implementationType;
        }

        public BiFunction<? extends S, String, ? super MutableModelNode> getFactory() {
            return factory;
        }
    }

    private static class InternalViewRegistration {
        private final ModelRuleDescriptor source;
        private final ModelType<?> internalView;

        private InternalViewRegistration(ModelRuleDescriptor source, ModelType<?> internalView) {
            this.source = source;
            this.internalView = internalView;
        }

        public ModelRuleDescriptor getSource() {
            return source;
        }

        public ModelType<?> getInternalView() {
            return internalView;
        }
    }


    private static class ImplementationInfoImpl<T> implements ImplementationInfo<T> {
        private final ModelType<? extends T> publicType;
        private final ModelType<? extends T> delegateType;

        public ImplementationInfoImpl(ModelType<? extends T> publicType, ModelType<? extends T> delegateType) {
            this.publicType = publicType;
            this.delegateType = delegateType;
        }

        public ModelType<? extends T> getPublicType() {
            return publicType;
        }

        public ModelType<? extends T> getDelegateType() {
            return delegateType;
        }

        @Override
        public String toString() {
            return String.valueOf(publicType);
        }
    }
}
