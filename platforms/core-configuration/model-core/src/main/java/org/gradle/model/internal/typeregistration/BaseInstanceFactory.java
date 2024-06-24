/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.typeregistration;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.Cast;
import org.gradle.internal.MutableReference;
import org.gradle.internal.reflect.Types.TypeVisitResult;
import org.gradle.internal.reflect.Types.TypeVisitor;
import org.gradle.model.Managed;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.reflect.Types.walkTypeHierarchy;

public class BaseInstanceFactory<PUBLIC> implements InstanceFactory<PUBLIC> {
    private final ModelType<PUBLIC> baseInterface;
    private final Map<ModelType<? extends PUBLIC>, TypeRegistration<? extends PUBLIC>> registrations = new LinkedHashMap<>();
    private final Map<Class<?>, ImplementationFactory<? extends PUBLIC, ?>> factories = new LinkedHashMap<>();

    public BaseInstanceFactory(Class<PUBLIC> baseInterface) {
        this.baseInterface = ModelType.of(baseInterface);
    }

    @Override
    public ModelType<PUBLIC> getBaseInterface() {
        return baseInterface;
    }

    public <S extends PUBLIC> void register(ModelType<S> publicType, Set<Class<?>> internalViews, @Nullable ModelType<?> implementationType, ModelRuleDescriptor definedBy) {
        TypeRegistrationBuilder<S> registration = register(publicType, definedBy);
        if (implementationType != null) {
            registration.withImplementation(implementationType);
        }
        for (Class<?> internalView : internalViews) {
            registration.withInternalView(ModelType.of(internalView));
        }
    }

    /**
     * Registers a factory to use to create all implementation objects of the given type <em>and its subtypes</em>. The most specific match is used.
     */
    public <S extends PUBLIC, I> void registerFactory(Class<I> implementationType, ImplementationFactory<S, I> implementationFactory) {
        factories.put(implementationType, implementationFactory);
    }

    public <S extends PUBLIC> TypeRegistrationBuilder<S> register(ModelType<S> publicType, ModelRuleDescriptor source) {
        TypeRegistration<S> registration = Cast.uncheckedCast(registrations.get(publicType));
        if (registration == null) {
            registration = new TypeRegistration<S>(publicType);
            registrations.put(publicType, registration);
        }
        return new TypeRegistrationBuilderImpl<S>(source, registration);
    }

    @Override @Nullable
    public <S extends PUBLIC> ImplementationInfo getImplementationInfo(ModelType<S> publicType) {
        ImplementationRegistration<S> implementationRegistration = getImplementationRegistration(publicType);
        if (implementationRegistration == null) {
            return null;
        }
        return new ImplementationInfoImpl<S>(publicType, implementationRegistration, getInternalViews(publicType));
    }

    @Override
    public <S extends PUBLIC> ImplementationInfo getManagedSubtypeImplementationInfo(final ModelType<S> publicType) {
        if (!isManaged(publicType)) {
            throw new IllegalArgumentException(String.format("Type '%s' is not managed", publicType));
        }
        final MutableReference<ImplementationInfoImpl<S>> implementationInfo = MutableReference.empty();
        walkTypeHierarchy(publicType.getConcreteClass(), new RegistrationHierarchyVisitor<S>() {
            @Override
            protected void visitRegistration(TypeRegistration<? extends PUBLIC> registration) {
                if (registration == null || registration.implementationRegistration == null) {
                    return;
                }
                ImplementationInfoImpl<S> currentImplementationInfo = implementationInfo.get();
                if (currentImplementationInfo == null || currentImplementationInfo.implementationRegistration.getImplementationType().isAssignableFrom(registration.implementationRegistration.getImplementationType())) {
                    implementationInfo.set(new ImplementationInfoImpl<S>(publicType, registration.implementationRegistration, getInternalViews(publicType)));
                }
            }
        });

        if (implementationInfo.get() == null) {
            throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because it doesn't extend an interface with a default implementation", publicType));
        }
        return implementationInfo.get();
    }

    private <S extends PUBLIC> Set<ModelType<?>> getInternalViews(ModelType<S> publicType) {
        final ImmutableSet.Builder<ModelType<?>> builder = ImmutableSet.builder();
        walkTypeHierarchy(publicType.getConcreteClass(), new RegistrationHierarchyVisitor<S>() {
            @Override
            protected void visitRegistration(TypeRegistration<? extends PUBLIC> registration) {
                for (InternalViewRegistration<?> internalViewRegistration : registration.internalViewRegistrations) {
                    builder.add(internalViewRegistration.getInternalView());
                }
            }
        });
        return builder.build();
    }

    @Override
    public Set<ModelType<? extends PUBLIC>> getSupportedTypes() {
        ImmutableSortedSet.Builder<ModelType<? extends PUBLIC>> supportedTypes = ImmutableSortedSet.orderedBy(ModelTypes.<PUBLIC>displayOrder());
        for (TypeRegistration<?> registration : registrations.values()) {
            if (registration.isConstructible()) {
                supportedTypes.add(registration.publicType);
            }
        }
        return supportedTypes.build();
    }

    private <S extends PUBLIC> TypeRegistration<S> getRegistration(ModelType<S> type) {
        return Cast.uncheckedCast(registrations.get(type));
    }

    private <S extends PUBLIC> ImplementationRegistration<S> getImplementationRegistration(ModelType<S> type) {
        TypeRegistration<S> registration = getRegistration(type);
        if (registration == null) {
            return null;
        }
        if (registration.implementationRegistration == null) {
            throw new IllegalArgumentException(
                String.format("Cannot create a '%s' because this type does not have an implementation registered.", type));
        }
        return registration.implementationRegistration;
    }

    public void validateRegistrations() {
        for (TypeRegistration<? extends PUBLIC> registration : registrations.values()) {
            registration.validate();
        }
    }

    private static boolean isManaged(ModelType<?> type) {
        return type.isAnnotationPresent(Managed.class);
    }

    public interface ImplementationFactory<PUBLIC, BASEIMPL> {
        <T extends BASEIMPL> T create(ModelType<? extends PUBLIC> publicType, ModelType<T> implementationType, String name, MutableModelNode node);
    }

    public interface TypeRegistrationBuilder<T> {
        TypeRegistrationBuilder<T> withImplementation(ModelType<?> implementationType);

        TypeRegistrationBuilder<T> withInternalView(ModelType<?> internalView);
    }

    private class TypeRegistrationBuilderImpl<S extends PUBLIC> implements TypeRegistrationBuilder<S> {
        private final ModelRuleDescriptor source;
        private final TypeRegistration<S> registration;

        public TypeRegistrationBuilderImpl(ModelRuleDescriptor source, TypeRegistration<S> registration) {
            this.source = source;
            this.registration = registration;
        }

        @Override
        public TypeRegistrationBuilder<S> withImplementation(ModelType<?> implementationType) {
            registration.setImplementation(implementationType, source);
            return this;
        }

        @Override
        public TypeRegistrationBuilder<S> withInternalView(ModelType<?> internalView) {
            registration.addInternalView(internalView, source);
            return this;
        }
    }

    private class TypeRegistration<S extends PUBLIC> {
        private final ModelType<S> publicType;
        private final boolean managedPublicType;
        private ImplementationRegistration<S> implementationRegistration;
        private final List<InternalViewRegistration<?>> internalViewRegistrations = new ArrayList<>();

        public TypeRegistration(ModelType<S> publicType) {
            this.publicType = publicType;
            this.managedPublicType = isManaged(publicType);
        }

        public void setImplementation(ModelType<?> implementationType, ModelRuleDescriptor source) {
            if (implementationRegistration != null) {
                throw new IllegalStateException(String.format("Cannot register implementation for type '%s' because an implementation for this type was already registered by %s",
                    publicType, implementationRegistration.getSource()));
            }
            if (managedPublicType) {
                throw new IllegalArgumentException(String.format("Cannot specify default implementation for managed type '%s'", publicType));
            }
            if (Modifier.isAbstract(implementationType.getConcreteClass().getModifiers())) {
                throw new IllegalArgumentException(String.format("Implementation type '%s' registered for '%s' must not be abstract", implementationType, publicType));
            }
            Class<?> implementationClass = implementationType.getConcreteClass();
            ImplementationFactory<S, ?> factory = findFactory(implementationClass);
            if (factory == null) {
                throw new IllegalArgumentException(String.format("No factory registered to create an instance of implementation class '%s'.", implementationType));
            }
            this.implementationRegistration = new ImplementationRegistration<S>(source, implementationType, factory);
        }

        public <V> void addInternalView(ModelType<V> internalView, ModelRuleDescriptor source) {
            if (!internalView.getConcreteClass().isInterface()) {
                throw new IllegalArgumentException(String.format("Internal view '%s' registered for '%s' must be an interface", internalView, publicType));
            }

            if (managedPublicType && !isManaged(internalView)) {
                throw new IllegalArgumentException(String.format("Internal view '%s' registered for managed type '%s' must be managed", internalView, publicType));
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

        public boolean isConstructible() {
            return managedPublicType || implementationRegistration != null;
        }

        private void validateManaged() {
            ImplementationInfo implementationInfo = getManagedSubtypeImplementationInfo(publicType);
            ModelType<?> delegateType = implementationInfo.getDelegateType();
            for (InternalViewRegistration<?> internalViewRegistration : internalViewRegistrations) {
                validateManagedInternalView(internalViewRegistration, delegateType);
            }
        }

        private <V> void validateManagedInternalView(final InternalViewRegistration<V> internalViewRegistration, final ModelType<?> delegateType) {
            walkTypeHierarchy(internalViewRegistration.getInternalView().getConcreteClass(), new TypeVisitor<V>() {
                @Override
                public TypeVisitResult visitType(Class<? super V> type) {
                    if (!type.isAnnotationPresent(Managed.class)
                        && !type.isAssignableFrom(delegateType.getConcreteClass())) {
                        throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because the default implementation type '%s' does not implement unmanaged internal view '%s', "
                                + "internal view was registered by %s",
                            publicType, delegateType, ModelType.of(type),
                            internalViewRegistration.getSource()));
                    }
                    return TypeVisitResult.CONTINUE;
                }
            });
        }

        private void validateUnmanaged() {
            if (implementationRegistration == null) {
                return;
            }

            ModelType<?> implementationType = implementationRegistration.getImplementationType();
            for (InternalViewRegistration<?> internalViewRegistration : internalViewRegistrations) {
                ModelType<?> internalView = internalViewRegistration.getInternalView();
                // Managed internal views are allowed not to be implemented by the default implementation
                if (isManaged(internalView)) {
                    continue;
                }
                if (!internalView.isAssignableFrom(implementationType)) {
                    throw new IllegalStateException(String.format(
                        "Factory registration for '%s' is invalid because the implementation type '%s' does not implement internal view '%s', "
                            + "implementation type was registered by %s, "
                            + "internal view was registered by %s",
                        publicType, implementationType, internalView,
                        implementationRegistration.getSource(),
                        internalViewRegistration.getSource()));
                }
            }
        }
    }

    @Nullable
    private <S extends PUBLIC> ImplementationFactory<S, ?> findFactory(Class<?> implementationClass) {
        ImplementationFactory<? extends PUBLIC, ?> implementationFactory = factories.get(implementationClass);
        if (implementationFactory != null) {
            return Cast.uncheckedCast(implementationFactory);
        }

        Class<?> superclass = implementationClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            implementationFactory = findFactory(superclass);
            factories.put(implementationClass, implementationFactory);
            return Cast.uncheckedCast(implementationFactory);
        }

        return null;
    }

    private static class ImplementationRegistration<PUBLIC> {
        private final ModelRuleDescriptor source;
        private final ModelType<?> implementationType;
        private final ImplementationFactory<? super PUBLIC, ?> factory;

        private ImplementationRegistration(ModelRuleDescriptor source, ModelType<?> implementationType, ImplementationFactory<? super PUBLIC, ?> factory) {
            this.source = source;
            this.implementationType = implementationType;
            this.factory = factory;
        }

        public ModelRuleDescriptor getSource() {
            return source;
        }

        public ModelType<?> getImplementationType() {
            return implementationType;
        }

        public ImplementationFactory<? super PUBLIC, ?> getFactory() {
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

    private static class ImplementationInfoImpl<PUBLIC> implements ImplementationInfo {
        private final ModelType<PUBLIC> publicType;
        private final ImplementationRegistration<? super PUBLIC> implementationRegistration;
        private final Set<ModelType<?>> internalViews;

        public ImplementationInfoImpl(ModelType<PUBLIC> publicType, ImplementationRegistration<?> implementationRegistration, Set<ModelType<?>> internalViews) {
            this.publicType = publicType;
            this.internalViews = internalViews;
            this.implementationRegistration = Cast.uncheckedCast(implementationRegistration);
        }

        @Override
        public Object create(MutableModelNode modelNode) {
            ImplementationFactory<PUBLIC, Object> implementationFactory = Cast.uncheckedNonnullCast(implementationRegistration.getFactory());
            return implementationFactory.create(publicType, implementationRegistration.getImplementationType(), modelNode.getPath().getName(), modelNode);
        }

        @Override
        public ModelType<?> getDelegateType() {
            return implementationRegistration.getImplementationType();
        }

        @Override
        public Set<ModelType<?>> getInternalViews() {
            return internalViews;
        }

        @Override
        public String toString() {
            return String.valueOf(publicType);
        }
    }

    private abstract class RegistrationHierarchyVisitor<S> implements TypeVisitor<S> {
        @Override
        public TypeVisitResult visitType(Class<? super S> type) {
            if (!baseInterface.getConcreteClass().isAssignableFrom(type)) {
                return TypeVisitResult.CONTINUE;
            }
            Class<? extends PUBLIC> superTypeClassAsBaseType = type.asSubclass(baseInterface.getConcreteClass());
            ModelType<? extends PUBLIC> superTypeAsBaseType = ModelType.of(superTypeClassAsBaseType);

            TypeRegistration<? extends PUBLIC> registration = getRegistration(superTypeAsBaseType);
            if (registration != null) {
                visitRegistration(registration);
            }
            return TypeVisitResult.CONTINUE;
        }

        protected abstract void visitRegistration(TypeRegistration<? extends PUBLIC> registration);
    }
}
