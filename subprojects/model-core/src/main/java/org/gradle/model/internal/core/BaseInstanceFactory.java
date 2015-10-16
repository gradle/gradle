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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
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
            registration = new TypeRegistration<S>(publicType);
            registrations.put(publicType, registration);
        }
        return new TypeRegistrationBuilderImpl<S>(source, registration);
    }

    @Override
    public <S extends T> ManagedSubtypeImplementationInfo<? extends T> getManagedSubtypeImplementationInfo(ModelType<S> managedType) {
        if (!managedType.getConcreteClass().isAnnotationPresent(Managed.class)) {
            throw new IllegalArgumentException(String.format("Type '%s' is not managed", managedType));
        }
        final List<ManagedSubtypeImplementationInfo<? extends T>> implementationInfos = Lists.newArrayListWithCapacity(1);
        ModelSchemaUtils.walkTypeHierarchy(managedType.getConcreteClass(), new RegistrationHierarchyVisitor<S>() {
            @Override
            protected void visitRegistration(TypeRegistration<? extends T> registration) {
                if (registration != null && registration.implementationRegistration != null) {
                    ModelType<? extends T> implementationType = registration.implementationRegistration.getImplementationType();
                    implementationInfos.add(new ManagedSubtypeImplementationInfoImpl<T>(registration.publicType, implementationType));
                }
            }
        });
        switch (implementationInfos.size()) {
            case 1:
                return implementationInfos.get(0);
            case 0:
                throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because it doesn't extend an interface with a default implementation", managedType));
            default:
                throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because it has multiple default implementations registered, super-types that registered an implementation are: %s",
                    managedType,
                    Joiner.on(", ").join(implementationInfos)));
        }
    }

    @Override
    public <S extends T> Set<ModelType<?>> getInternalViews(ModelType<S> publicType) {
        final ImmutableSet.Builder<ModelType<?>> builder = ImmutableSet.builder();
        ModelSchemaUtils.walkTypeHierarchy(publicType.getConcreteClass(), new RegistrationHierarchyVisitor<S>() {
            @Override
            protected void visitRegistration(TypeRegistration<? extends T> registration) {
                for (InternalViewRegistration<?> internalViewRegistration : registration.internalViewRegistrations) {
                    builder.add(internalViewRegistration.getInternalView());
                }
            }
        });
        return builder.build();
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

    private <S extends T> TypeRegistration<S> getRegistration(ModelType<S> type) {
        return Cast.uncheckedCast(registrations.get(type));
    }

    @Override
    public void validateRegistrations() {
        for (TypeRegistration<? extends T> registration : registrations.values()) {
            registration.validate();
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
        private final ModelType<S> publicType;
        private final boolean managedPublicType;
        private ImplementationRegistration<S> implementationRegistration;
        private final List<InternalViewRegistration<?>> internalViewRegistrations = Lists.newArrayList();

        public TypeRegistration(ModelType<S> publicType) {
            this.publicType = publicType;
            this.managedPublicType = publicType.getConcreteClass().isAnnotationPresent(Managed.class);
        }

        public void setImplementation(ModelType<? extends S> implementationType, ModelRuleDescriptor source, BiFunction<? extends S, String, ? super MutableModelNode> factory) {
            if (implementationRegistration != null) {
                throw new IllegalStateException(String.format("Cannot register implementation for type '%s' because an implementation for this type was already registered by %s",
                    publicType, implementationRegistration.getSource()));
            }
            if (managedPublicType) {
                throw new IllegalArgumentException(String.format("Cannot specify default implementation for managed type '%s'", publicType));
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

        public <V> void addInternalView(ModelType<V> internalView, ModelRuleDescriptor source) {
            if (!internalView.getConcreteClass().isInterface()) {
                throw new IllegalArgumentException(String.format("Internal view '%s' registered for '%s' must be an interface", internalView, publicType));
            }

            boolean managedInternalView = internalView.getConcreteClass().isAnnotationPresent(Managed.class);
            if (managedPublicType && !managedInternalView) {
                throw new IllegalArgumentException(String.format("Internal view '%s' registered for managed type '%s' must be managed", internalView, publicType));
            }
            if (!managedPublicType && managedInternalView) {
                throw new IllegalArgumentException(String.format("Internal view '%s' registered for unmanaged type '%s' must be unmanaged", internalView, publicType));
            }
            internalViewRegistrations.add(new InternalViewRegistration<V>(source, internalView));
        }

        public void validate() {
            if (managedPublicType) {
                validateManaged();
            } else {
                validateUnmanaged();
            }
        }

        private void validateManaged() {
            ManagedSubtypeImplementationInfo<? extends T> implementationInfo = getManagedSubtypeImplementationInfo(publicType);
            ModelType<? extends T> delegateType = implementationInfo.getDelegateType();
            for (InternalViewRegistration<?> internalViewRegistration : internalViewRegistrations) {
                validateManagedInternalView(internalViewRegistration, delegateType);
            }
        }

        private <V> void validateManagedInternalView(final InternalViewRegistration<V> internalViewRegistration, final ModelType<? extends T> delegateType) {
            ModelSchemaUtils.walkTypeHierarchy(internalViewRegistration.getInternalView().getConcreteClass(), new ModelSchemaUtils.TypeVisitor<V>() {
                @Override
                public void visitType(Class<? super V> type) {
                    if (!type.isAnnotationPresent(Managed.class)
                        && !type.isAssignableFrom(delegateType.getConcreteClass())) {
                        throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because the default implementation type '%s' does not implement unmanaged internal view '%s', "
                                + "internal view was registered by %s",
                            publicType, delegateType, type.getName(),
                            internalViewRegistration.getSource()));
                    }
                }
            });
        }

        private void validateUnmanaged() {
            if (implementationRegistration == null) {
                throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because no implementation was registered", publicType));
            }

            ModelType<? extends S> implementationType = implementationRegistration.getImplementationType();
            for (InternalViewRegistration<?> internalViewRegistration : internalViewRegistrations) {
                ModelType<?> internalView = internalViewRegistration.getInternalView();
                if (!internalView.isAssignableFrom(implementationType)) {
                    throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because the implementation type '%s' does not implement internal view '%s', "
                            + "implementation type was registered by %s, "
                            + "internal view was registered by %s",
                        publicType, implementationType, internalView,
                        implementationRegistration.getSource(),
                        internalViewRegistration.getSource()));
                }
            }
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

    private static class InternalViewRegistration<T> {
        private final ModelRuleDescriptor source;
        private final ModelType<T> internalView;

        private InternalViewRegistration(ModelRuleDescriptor source, ModelType<T> internalView) {
            this.source = source;
            this.internalView = internalView;
        }

        public ModelRuleDescriptor getSource() {
            return source;
        }

        public ModelType<T> getInternalView() {
            return internalView;
        }
    }


    private static class ManagedSubtypeImplementationInfoImpl<T> implements ManagedSubtypeImplementationInfo<T> {
        private final ModelType<? extends T> publicType;
        private final ModelType<? extends T> delegateType;

        public ManagedSubtypeImplementationInfoImpl(ModelType<? extends T> publicType, ModelType<? extends T> delegateType) {
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

    private abstract class RegistrationHierarchyVisitor<S> implements ModelSchemaUtils.TypeVisitor<S> {
        @Override
        public void visitType(Class<? super S> type) {
            if (!baseInterface.getConcreteClass().isAssignableFrom(type)) {
                return;
            }
            Class<? extends T> superTypeClassAsBaseType = type.asSubclass(baseInterface.getConcreteClass());
            ModelType<? extends T> superTypeAsBaseType = ModelType.of(superTypeClassAsBaseType);

            TypeRegistration<? extends T> registration = getRegistration(superTypeAsBaseType);
            if (registration != null) {
                visitRegistration(registration);
            }
        }

        protected abstract void visitRegistration(TypeRegistration<? extends T> registration);
    }
}
