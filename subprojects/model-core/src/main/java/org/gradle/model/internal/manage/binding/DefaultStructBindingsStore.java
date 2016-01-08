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

package org.gradle.model.internal.manage.binding;

import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils;
import org.gradle.model.internal.manage.schema.extract.PropertyAccessorType;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.gradle.internal.reflect.Methods.DESCRIPTOR_EQUIVALENCE;
import static org.gradle.internal.reflect.Methods.SIGNATURE_EQUIVALENCE;
import static org.gradle.model.internal.manage.schema.extract.PropertyAccessorType.*;

public class DefaultStructBindingsStore implements StructBindingsStore {
    private final LoadingCache<CacheKey, StructBindings<?>> bindings = CacheBuilder.newBuilder()
        .weakValues()
        .build(new CacheLoader<CacheKey, StructBindings<?>>() {
            @Override
            public StructBindings<?> load(CacheKey key) throws Exception {
                return extract(key.publicType, key.viewTypes, key.delegateType);
            }
        });

    private final ModelSchemaStore schemaStore;

    public DefaultStructBindingsStore(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }

    @Override
    public <T> StructBindings<T> getBindings(ModelType<T> publicType) {
        return getBindings(publicType, Collections.<ModelType<?>>emptySet(), null);
    }

    @Override
    public <T> StructBindings<T> getBindings(ModelType<T> publicType, Iterable<? extends ModelType<?>> internalViewTypes, ModelType<?> delegateType) {
        try {
            return Cast.uncheckedCast(bindings.get(new CacheKey(publicType, internalViewTypes, delegateType)));
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    <T, D> StructBindings<T> extract(ModelType<T> publicType, Iterable<? extends ModelType<?>> internalViewTypes, ModelType<D> delegateType) {
        if (delegateType != null && Modifier.isAbstract(delegateType.getConcreteClass().getModifiers())) {
            throw new IllegalArgumentException(String.format("Delegate '%s' type must be null or a non-abstract type", delegateType));
        }

        Set<ModelType<?>> implementedViews = collectImplementedViews(publicType, internalViewTypes, delegateType);
        StructSchema<T> publicSchema = getStructSchema(publicType);
        Iterable<StructSchema<?>> declaredViewSchemas = getStructSchemas(Iterables.concat(Collections.singleton(publicType), internalViewTypes));
        Iterable<StructSchema<?>> implementedSchemas = getStructSchemas(implementedViews);
        StructSchema<?> delegateSchema = delegateType == null ? null : getStructSchema(delegateType);

        // TODO:LPTR Validate view types have no fields

        Map<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyBindings = Maps.newTreeMap();
        Set<StructMethodBinding> methodBindings = collectMethodBindings(publicSchema, delegateSchema, implementedSchemas, propertyBindings);
        ImmutableSortedMap<String, ManagedProperty<?>> managedProperties = collectManagedProperties(publicSchema, propertyBindings);

        return new DefaultStructBindings<T>(
            publicSchema, declaredViewSchemas, implementedSchemas, delegateSchema,
            managedProperties, methodBindings
        );
    }

    private static <T> ImmutableSortedMap<String, ManagedProperty<?>> collectManagedProperties(StructSchema<T> publicSchema, Map<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyBindings) {
        ImmutableSortedMap.Builder<String, ManagedProperty<?>> managedPropertiesBuilder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyEntry : propertyBindings.entrySet()) {
            String propertyName = propertyEntry.getKey();
            Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings = propertyEntry.getValue();

            if (isManagedProperty(propertyName, accessorBindings)) {
                boolean foundGetter = accessorBindings.containsKey(GET_GETTER) || accessorBindings.containsKey(IS_GETTER);
                boolean foundSetter = accessorBindings.containsKey(SETTER);
                if (foundSetter && !foundGetter) {
                    throw new IllegalArgumentException(String.format("Managed property '%s' must both have an abstract getter as well as a setter.", propertyName));
                }

                ModelType<?> propertyType = determinePropertyType(propertyName, accessorBindings);
                boolean writable = accessorBindings.containsKey(SETTER);
                boolean declaredAsUnmanaged = isDeclaredAsHavingUnmanagedType(accessorBindings.get(GET_GETTER))
                    || isDeclaredAsHavingUnmanagedType(accessorBindings.get(IS_GETTER));
                boolean internal = !publicSchema.hasProperty(propertyName);
                managedPropertiesBuilder.put(propertyName, createProperty(propertyName, propertyType, writable, declaredAsUnmanaged, internal));
            }
        }
        return managedPropertiesBuilder.build();
    }

    private static boolean isManagedProperty(String propertyName, Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings) {
        Boolean managed = null;
        for (Map.Entry<PropertyAccessorType, Collection<StructMethodBinding>> accessorEntry : accessorBindings.asMap().entrySet()) {
            Collection<StructMethodBinding> bindings = accessorEntry.getValue();
            boolean managedPropertyAccessor = isManagedPropertyAccessor(propertyName, bindings);
            if (managed == null) {
                managed = managedPropertyAccessor;
            } else if (managed != managedPropertyAccessor) {
                throw new IllegalArgumentException(String.format("The accessor methods belonging to property '%s' should either all have an implementation "
                    + "provided by the view itself or a default implementation, or they should all be abstract.", propertyName));
            }
        }
        assert managed != null;
        return managed;
    }

    private static boolean isManagedPropertyAccessor(String propertyName, Collection<StructMethodBinding> bindings) {
        Set<WeaklyTypeReferencingMethod<?, ?>> implMethods = Sets.newLinkedHashSet();
        for (StructMethodBinding binding : bindings) {
            if (binding instanceof StructMethodImplementationBinding) {
                implMethods.add(((StructMethodImplementationBinding) binding).getImplementor());
            }
        }
        switch (implMethods.size()) {
            case 0:
                return true;
            case 1:
                return false;
            default:
                throw new IllegalArgumentException(String.format("Property '%s' has multiple implementations for accessor method: %s",
                    propertyName, Joiner.on(", ").join(implMethods)));
        }
    }

    private static ModelType<?> determinePropertyType(String propertyName, Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings) {
        // TODO:LPTR Figure out property type from getters, and validated it against setter
        Set<ModelType<?>> potentialPropertyTypes = Sets.newLinkedHashSet();
        for (StructMethodBinding binding : accessorBindings.values()) {
            ManagedPropertyMethodBinding propertyBinding = (ManagedPropertyMethodBinding) binding;
            potentialPropertyTypes.add(propertyBinding.getDeclaredPropertyType());
        }
        Collection<ModelType<?>> convergingPropertyTypes = findConvergingTypes(potentialPropertyTypes);
        if (convergingPropertyTypes.size() != 1) {
            throw new IllegalArgumentException(String.format("Managed property '%s' must have a consistent type, but it's defined as %s.", propertyName,
                Joiner.on(", ").join(ModelTypes.getDisplayNames(convergingPropertyTypes))));
        }
        return Iterables.getOnlyElement(convergingPropertyTypes);
    }

    private static <T, D> Set<ModelType<?>> collectImplementedViews(ModelType<T> publicType, Iterable<? extends ModelType<?>> internalViewTypes, ModelType<D> delegateType) {
        final Set<ModelType<?>> viewsToImplement = Sets.newLinkedHashSet();
        viewsToImplement.add(publicType);
        Iterables.addAll(viewsToImplement, internalViewTypes);
        // TODO:LPTR This should be removed once BinaryContainer is a ModelMap
        // We need to also implement all the interfaces of the delegate type because otherwise
        // BinaryContainer won't recognize managed binaries as BinarySpecInternal
        if (delegateType != null) {
            ModelSchemaUtils.walkTypeHierarchy(delegateType.getConcreteClass(), new ModelSchemaUtils.TypeVisitor<D>() {
                @Override
                public void visitType(Class<? super D> type) {
                    if (type.isInterface()) {
                        viewsToImplement.add(ModelType.of(type));
                    }
                }
            });
        }
        return ModelTypes.collectHierarchy(viewsToImplement);
    }

    private static <T> Set<StructMethodBinding> collectMethodBindings(StructSchema<T> publicSchema, StructSchema<?> delegateSchema, Iterable<StructSchema<?>> implementedSchemas, Map<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyBindings) {
        Collection<WeaklyTypeReferencingMethod<?, ?>> implementedMethods = collectImplementedMethods(implementedSchemas);
        Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> publicViewImplMethods = collectPublicViewImplMethods(publicSchema);
        Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> delegateMethods = collectDelegateMethods(delegateSchema);

        ImmutableSet.Builder<StructMethodBinding> methodBindingsBuilder = ImmutableSet.builder();
        for (WeaklyTypeReferencingMethod<?, ?> weakViewMethod : implementedMethods) {
            Method implementedMethod = weakViewMethod.getMethod();
            PropertyAccessorType accessorType = PropertyAccessorType.of(implementedMethod);

            Wrapper<Method> methodKey = SIGNATURE_EQUIVALENCE.wrap(implementedMethod);
            WeaklyTypeReferencingMethod<?, ?> weakDelegateImplMethod = delegateMethods.get(methodKey);
            WeaklyTypeReferencingMethod<?, ?> weakPublicImplMethod = publicViewImplMethods.get(methodKey);
            if (weakDelegateImplMethod != null && weakPublicImplMethod != null) {
                throw new IllegalArgumentException(String.format("Method '%s' is both implemented by the view and the delegate type '%s'",
                    weakViewMethod, weakDelegateImplMethod));
            }

            String propertyName = accessorType == null ? null : accessorType.propertyNameFor(implementedMethod);

            StructMethodBinding binding;
            if (!Modifier.isAbstract(implementedMethod.getModifiers())) {
                binding = new DirectMethodBinding(weakViewMethod, accessorType);
            } else if (weakPublicImplMethod != null) {
                binding = new BridgeMethodBinding(weakViewMethod, weakPublicImplMethod, accessorType);
            } else if (weakDelegateImplMethod != null) {
                binding = new DelegateMethodBinding(weakViewMethod, weakDelegateImplMethod, accessorType);
            } else if (propertyName != null) {
                binding = new ManagedPropertyMethodBinding(weakViewMethod, propertyName, accessorType);
            } else {
                if (delegateSchema == null) {
                    throw new IllegalArgumentException(String.format("Method '%s' is not a property accessor, and it has no implementation",
                        implementedMethod.toGenericString()));
                } else {
                    throw new IllegalArgumentException(String.format("Method '%s' is not a property accessor, and it has no implementation or a delegate in type '%s'",
                        implementedMethod.toGenericString(), delegateSchema.getType().getDisplayName()));
                }
            }
            methodBindingsBuilder.add(binding);

            if (accessorType != null) {
                Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings = propertyBindings.get(propertyName);
                if (accessorBindings == null) {
                    accessorBindings = ArrayListMultimap.create();
                    propertyBindings.put(propertyName, accessorBindings);
                }
                accessorBindings.put(accessorType, binding);
            }
        }
        return methodBindingsBuilder.build();
    }

    private static Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> collectDelegateMethods(StructSchema<?> delegateSchema) {
        return delegateSchema == null ? Collections.<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>>emptyMap() : indexBySignature(delegateSchema.getAllMethods());
    }

    private static <T> Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> collectPublicViewImplMethods(StructSchema<T> publicSchema) {
        return indexBySignature(
            Sets.filter(
                publicSchema.getAllMethods(),
                new Predicate<WeaklyTypeReferencingMethod<?, ?>>() {
                    @Override
                    public boolean apply(WeaklyTypeReferencingMethod<?, ?> weakMethod) {
                        return !Modifier.isAbstract(weakMethod.getModifiers());
                    }
                }
            )
        );
    }

    private static ImmutableMap<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> indexBySignature(Iterable<WeaklyTypeReferencingMethod<?, ?>> methods) {
        return Maps.uniqueIndex(methods, new Function<WeaklyTypeReferencingMethod<?, ?>, Wrapper<Method>>() {
            @Override
            public Wrapper<Method> apply(WeaklyTypeReferencingMethod<?, ?> weakMethod) {
                return SIGNATURE_EQUIVALENCE.wrap(weakMethod.getMethod());
            }
        });
    }

    private static Collection<WeaklyTypeReferencingMethod<?, ?>> collectImplementedMethods(Iterable<StructSchema<?>> implementedSchemas) {
        Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> implementedMethodsBuilder = Maps.newLinkedHashMap();
        for (StructSchema<?> implementedSchema : implementedSchemas) {
            for (WeaklyTypeReferencingMethod<?, ?> viewMethod : implementedSchema.getAllMethods()) {
                implementedMethodsBuilder.put(DESCRIPTOR_EQUIVALENCE.wrap(viewMethod.getMethod()), viewMethod);
            }
        }
        return implementedMethodsBuilder.values();
    }

    private static <T> ManagedProperty<T> createProperty(String propertyName, ModelType<T> propertyType, boolean writable, boolean declaredAsUnmanaged, boolean internal) {
        return new ManagedProperty<T>(propertyName, propertyType, writable, declaredAsUnmanaged, internal);
    }

    private static boolean isDeclaredAsHavingUnmanagedType(Collection<StructMethodBinding> accessorBindings) {
        for (StructMethodBinding accessorBinding : accessorBindings) {
            if (accessorBinding.getSource().getMethod().isAnnotationPresent(Unmanaged.class)) {
                return true;
            }
        }
        return false;
    }

    private <T> Iterable<StructSchema<? extends T>> getStructSchemas(Iterable<? extends ModelType<? extends T>> types) {
        return Iterables.transform(types, new Function<ModelType<? extends T>, StructSchema<? extends T>>() {
            @Override
            public StructSchema<? extends T> apply(ModelType<? extends T> type) {
                return  getStructSchema(type);
            }
        });
    }

    private <T> StructSchema<T> getStructSchema(ModelType<T> type) {
        ModelSchema<T> schema = schemaStore.getSchema(type);
        if (!(schema instanceof StructSchema)) {
            throw new IllegalArgumentException(String.format("Type '%s' is not a struct.", type.getDisplayName()));
        }
        return Cast.uncheckedCast(schema);
    }

    private static class CacheKey {
        private final ModelType<?> publicType;
        private final Set<ModelType<?>> viewTypes;
        private final ModelType<?> delegateType;

        public CacheKey(ModelType<?> publicType, Iterable<? extends ModelType<?>> viewTypes, ModelType<?> delegateType) {
            this.publicType = publicType;
            this.viewTypes = ImmutableSet.copyOf(viewTypes);
            this.delegateType = delegateType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equal(publicType, cacheKey.publicType)
                && Objects.equal(viewTypes, cacheKey.viewTypes)
                && Objects.equal(delegateType, cacheKey.delegateType);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(publicType, viewTypes, delegateType);
        }
    }

    /**
     * Finds the types in the given collection that cannot be assigned from any other type in the collection.
     */
    static Collection<ModelType<?>> findConvergingTypes(Collection<ModelType<?>> allTypes) {
        if (allTypes.size() == 0) {
            throw new IllegalArgumentException("No types given");
        }
        if (allTypes.size() == 1) {
            return allTypes;
        }

        Set<ModelType<?>> typesToCheck = Sets.newLinkedHashSet(allTypes);
        Set<ModelType<?>> convergingTypes = Sets.newLinkedHashSet(allTypes);

        while (!typesToCheck.isEmpty()) {
            Iterator<ModelType<?>> iTypeToCheck = typesToCheck.iterator();
            ModelType<?> typeToCheck = iTypeToCheck.next();
            iTypeToCheck.remove();

            Iterator<ModelType<?>> iRemainingType = convergingTypes.iterator();
            while (iRemainingType.hasNext()) {
                ModelType<?> remainingType = iRemainingType.next();
                if (!remainingType.equals(typeToCheck) && remainingType.isAssignableFrom(typeToCheck)) {
                    iRemainingType.remove();
                    typesToCheck.remove(remainingType);
                }
            }
        }

        return convergingTypes;
    }
}
