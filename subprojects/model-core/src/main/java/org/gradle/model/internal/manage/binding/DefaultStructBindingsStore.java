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
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.Named;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.*;
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
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
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

    private <T> ImmutableSortedMap<String, ManagedProperty<?>> collectManagedProperties(StructSchema<T> publicSchema, Map<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyBindings) {
        ImmutableSortedMap.Builder<String, ManagedProperty<?>> managedPropertiesBuilder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyEntry : propertyBindings.entrySet()) {
            String propertyName = propertyEntry.getKey();
            Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings = propertyEntry.getValue();

            if (isManagedProperty(publicSchema.getType(), propertyName, accessorBindings)) {
                boolean foundGetter = accessorBindings.containsKey(GET_GETTER) || accessorBindings.containsKey(IS_GETTER);
                boolean foundSetter = accessorBindings.containsKey(SETTER);
                if (foundSetter && !foundGetter) {
                    throw new InvalidManagedPropertyException(publicSchema.getType(), propertyName,
                        "must both have an abstract getter as well as a setter");
                }

                ModelType<?> propertyType = determineManagedPropertyType(publicSchema.getType(), propertyName, accessorBindings);
                ModelSchema<?> propertySchema = schemaStore.getSchema(propertyType);
                managedPropertiesBuilder.put(propertyName, createManagedProperty(publicSchema, propertyName, propertySchema, accessorBindings));
            }
        }
        return managedPropertiesBuilder.build();
    }

    private static boolean isManagedProperty(ModelType<?> publicType, String propertyName, Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings) {
        Boolean managed = null;
        for (Map.Entry<PropertyAccessorType, Collection<StructMethodBinding>> accessorEntry : accessorBindings.asMap().entrySet()) {
            Collection<StructMethodBinding> bindings = accessorEntry.getValue();
            boolean managedPropertyAccessor = isManagedPropertyAccessor(propertyName, bindings);
            if (managed == null) {
                managed = managedPropertyAccessor;
            } else if (managed != managedPropertyAccessor) {
                throw new InvalidManagedPropertyException(publicType, propertyName,
                    "must have either only abstract accessor methods or only implemented accessor methods");
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

    private static ModelType<?> determineManagedPropertyType(ModelType<?> publicType, String propertyName, Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings) {
        Set<ModelType<?>> potentialPropertyTypes = Sets.newLinkedHashSet();
        for (StructMethodBinding binding : accessorBindings.values()) {
            if (binding.getAccessorType() == SETTER) {
                continue;
            }
            ManagedPropertyMethodBinding propertyBinding = (ManagedPropertyMethodBinding) binding;
            potentialPropertyTypes.add(propertyBinding.getDeclaredPropertyType());
        }
        Collection<ModelType<?>> convergingPropertyTypes = findConvergingTypes(potentialPropertyTypes);
        if (convergingPropertyTypes.size() != 1) {
            throw new InvalidManagedPropertyException(publicType, propertyName,
                String.format("must have a consistent type, but it's defined as %s.",
                    Joiner.on(", ").join(ModelTypes.getDisplayNames(convergingPropertyTypes))));
        }
        ModelType<?> propertyType = Iterables.getOnlyElement(convergingPropertyTypes);

        for (StructMethodBinding setterBinding : accessorBindings.get(SETTER)) {
            ManagedPropertyMethodBinding propertySetterBinding = (ManagedPropertyMethodBinding) setterBinding;
            ModelType<?> declaredSetterType = propertySetterBinding.getDeclaredPropertyType();
            if (!declaredSetterType.equals(propertyType)) {
                throw new InvalidManagedPropertyException(publicType, propertyName,
                    String.format("must have setter accepting parameter with type '%s' instead of '%s'",
                        propertyType.getDisplayName(), declaredSetterType.getDisplayName()));
            }
        }
        return propertyType;
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
        for (WeaklyTypeReferencingMethod<?, ?> weakImplementedMethod : implementedMethods) {
            Method implementedMethod = weakImplementedMethod.getMethod();
            PropertyAccessorType accessorType = PropertyAccessorType.of(implementedMethod);

            Wrapper<Method> methodKey = SIGNATURE_EQUIVALENCE.wrap(implementedMethod);
            WeaklyTypeReferencingMethod<?, ?> weakDelegateImplMethod = delegateMethods.get(methodKey);
            WeaklyTypeReferencingMethod<?, ?> weakPublicImplMethod = publicViewImplMethods.get(methodKey);
            if (weakDelegateImplMethod != null && weakPublicImplMethod != null) {
                throw new IllegalArgumentException(String.format("Method '%s' is both implemented by the view and the delegate type '%s'",
                    weakImplementedMethod, weakDelegateImplMethod));
            }

            String propertyName = accessorType == null ? null : accessorType.propertyNameFor(implementedMethod);

            StructMethodBinding binding;
            if (!Modifier.isAbstract(implementedMethod.getModifiers())) {
                binding = new DirectMethodBinding(weakImplementedMethod, accessorType);
            } else if (weakPublicImplMethod != null) {
                binding = new BridgeMethodBinding(weakImplementedMethod, weakPublicImplMethod, accessorType);
            } else if (weakDelegateImplMethod != null) {
                binding = new DelegateMethodBinding(weakImplementedMethod, weakDelegateImplMethod, accessorType);
            } else if (propertyName != null) {
                binding = new ManagedPropertyMethodBinding(weakImplementedMethod, propertyName, accessorType);
            } else {
                throw handleNoMethodImplementation(publicSchema.getType(), weakImplementedMethod);
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

    private static RuntimeException handleNoMethodImplementation(ModelType<?> publicType, WeaklyTypeReferencingMethod<?, ?> method) {
        String methodName = method.getName();
        PropertyAccessorType accessorType = PropertyAccessorType.fromName(methodName);
        if (accessorType != null) {
            String propertyName = accessorType.propertyNameFor(methodName);
            switch (accessorType) {
                case GET_GETTER:
                case IS_GETTER:
                    if (!PropertyAccessorType.takesNoParameter(method.getMethod())) {
                        return new InvalidManagedPropertyException(publicType, propertyName,
                            String.format("is invalid: getter method '%s' must not take parameters", method));
                    }
                    break;
                case SETTER:
                    if (!hasVoidReturnType(method.getMethod())) {
                        return new InvalidManagedPropertyException(publicType, propertyName,
                            String.format("is invalid: setter method '%s' must have void return type", method));
                    }
                    if (!takesSingleParameter(method.getMethod())) {
                        return new InvalidManagedPropertyException(publicType, propertyName,
                            String.format("is invalid: setter method '%s' must have exactly one parameter", method));
                    }
                    break;
                default:
                    throw new AssertionError();
            }
        }
        return new IllegalArgumentException(String.format("Abstract method '%s' is not a property accessor, and it has no implementation.", method));
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

    private static <T> ManagedProperty<T> createManagedProperty(StructSchema<?> publicSchema, String propertyName, ModelSchema<T> propertySchema, Multimap<PropertyAccessorType, StructMethodBinding> accessors) {
        boolean writable = accessors.containsKey(SETTER);
        boolean declaredAsUnmanaged = isDeclaredAsHavingUnmanagedType(accessors.get(GET_GETTER))
            || isDeclaredAsHavingUnmanagedType(accessors.get(IS_GETTER));
        boolean internal = !publicSchema.hasProperty(propertyName);

        validateManagedProperty(publicSchema, propertyName, propertySchema, writable, declaredAsUnmanaged);

        return new ManagedProperty<T>(propertyName, propertySchema.getType(), writable, declaredAsUnmanaged, internal);
    }

    private static void validateManagedProperty(StructSchema<?> publicSchema, String propertyName, ModelSchema<?> propertySchema, boolean writable, boolean isDeclaredAsHavingUnmanagedType) {
        ModelType<?> publicType = publicSchema.getType();

        if (propertyName.equals("name") && Named.class.isAssignableFrom(publicType.getRawClass())) {
            if (writable) {
                throw new InvalidManagedPropertyException(publicType, propertyName,
                    String.format("must not have a setter, because the type implements '%s'", Named.class.getName()));
            }
            return;
        }

        // Only managed implementation and value types are allowed as a managed property type unless marked with @Unmanaged
        boolean isAllowedPropertyTypeOfManagedType = propertySchema instanceof ManagedImplSchema
            || propertySchema instanceof ScalarValueSchema;

        ModelType<?> propertyType = propertySchema.getType();

        if (isAllowedPropertyTypeOfManagedType && isDeclaredAsHavingUnmanagedType) {
            throw new InvalidManagedPropertyException(publicType, propertyName, String.format(
                "is marked as @Unmanaged, but is of @Managed type '%s'. Please remove the @Managed annotation",
                propertyType.getDisplayName()
            ));
        }

        if (!writable && isDeclaredAsHavingUnmanagedType) {
            throw new InvalidManagedPropertyException(publicType, propertyName,
                "must not be read only, because it is marked as @Unmanaged"
            );
        }

        if (!(publicSchema instanceof RuleSourceSchema)) {
            if (propertySchema instanceof CollectionSchema) {
                if (!(propertySchema instanceof ScalarCollectionSchema) && writable) {
                    throw new InvalidManagedPropertyException(publicType, propertyName, String.format(
                        "cannot have a setter (%s properties must be read only)",
                        propertyType.getRawClass().getSimpleName()));
                }
            }
        }
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
