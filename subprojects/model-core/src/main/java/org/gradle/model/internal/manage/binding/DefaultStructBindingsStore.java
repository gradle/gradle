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

import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.MethodSignatureEquivalence;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.manage.schema.extract.PropertyAccessorType;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class DefaultStructBindingsStore implements StructBindingsStore {
    private static final Equivalence<Method> METHOD_EQUIVALENCE = new MethodSignatureEquivalence();

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

    <T> StructBindings<T> extract(ModelType<T> publicType, Iterable<? extends ModelType<?>> internalViewTypes, ModelType<?> delegateType) {
        if (delegateType != null && Modifier.isAbstract(delegateType.getConcreteClass().getModifiers())) {
            throw new IllegalArgumentException(String.format("Delegate '%s' type must be null or a non-abstract type", delegateType));
        }

        StructSchema<T> publicSchema = getStructSchema(publicType);
        Iterable<StructSchema<?>> internalViewSchemas = getStructSchemas(internalViewTypes);
        StructSchema<?> delegateSchema = delegateType == null ? null : getStructSchema(delegateType);

        // TODO:LPTR Validate view types have no fields
        ImmutableSet.Builder<WeaklyTypeReferencingMethod<?, ?>> allViewMethodsBuilder = ImmutableSet.builder();

        StructSchema<T> publicStructSchema = Cast.uncheckedCast(publicSchema);
        allViewMethodsBuilder.addAll(publicStructSchema.getAllMethods());
        for (StructSchema<?> internalViewSchema : internalViewSchemas) {
            allViewMethodsBuilder.addAll(internalViewSchema.getAllMethods());
        }
        Set<WeaklyTypeReferencingMethod<?, ?>> viewMethods = allViewMethodsBuilder.build();

        Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> delegateMethods;
        if (delegateSchema == null) {
            delegateMethods = Collections.emptyMap();
        } else {
            delegateMethods = Maps.uniqueIndex(delegateSchema.getAllMethods(), new Function<WeaklyTypeReferencingMethod<?, ?>, Wrapper<Method>>() {
                @Override
                public Wrapper<Method> apply(WeaklyTypeReferencingMethod<?, ?> weakMethod) {
                    return METHOD_EQUIVALENCE.wrap(weakMethod.getMethod());
                }
            });
        }

        Multimap<String, AbstractStructMethodBinding> propertyMethodBindings = ArrayListMultimap.create();
        ImmutableSet.Builder<DirectMethodBinding> viewBindingsBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<DelegateMethodBinding> delegateBindingsBuilder = ImmutableSet.builder();

        for (WeaklyTypeReferencingMethod<?, ?> weakViewMethod : viewMethods) {
            Method viewMethod = weakViewMethod.getMethod();
            PropertyAccessorType accessorType = PropertyAccessorType.of(viewMethod);
            WeaklyTypeReferencingMethod<?, ?> weakDelegateMethod = delegateMethods.get(METHOD_EQUIVALENCE.wrap(viewMethod));
            AbstractStructMethodBinding binding;
            if (!Modifier.isAbstract(viewMethod.getModifiers())) {
                if (weakDelegateMethod != null) {
                    throw new IllegalArgumentException(String.format("Method '%s' is both implemented by the view and the delegate type '%s'",
                        viewMethod.toGenericString(), weakDelegateMethod.getMethod().toGenericString()));
                }
                DirectMethodBinding directBinding = new DirectMethodBinding(weakViewMethod);
                viewBindingsBuilder.add(directBinding);
                binding = directBinding;
            } else if (weakDelegateMethod != null) {
                binding = new DelegateMethodBinding(weakViewMethod, weakDelegateMethod);
                delegateBindingsBuilder.add((DelegateMethodBinding) binding);
            } else if (accessorType != null) {
                binding = new ManagedPropertyMethodBinding(weakViewMethod, accessorType);
            } else {
                if (delegateSchema == null) {
                    throw new IllegalArgumentException(String.format("Method '%s' is not a property accessor, and it has no implementation",
                        viewMethod.toGenericString()));
                } else {
                    throw new IllegalArgumentException(String.format("Method '%s' is not a property accessor, and it has no implementation or a delegate in type '%s'",
                        viewMethod.toGenericString(), delegateSchema.getType().getDisplayName()));
                }
            }
            if (accessorType != null) {
                propertyMethodBindings.put(accessorType.propertyNameFor(viewMethod), binding);
            }
        }

        ImmutableSortedMap.Builder<String, ManagedProperty<?>> generatedPropertiesBuilder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Collection<AbstractStructMethodBinding>> entry : propertyMethodBindings.asMap().entrySet()) {
            String propertyName = entry.getKey();
            Collection<AbstractStructMethodBinding> bindings = entry.getValue();
            Iterator<AbstractStructMethodBinding> iBinding = bindings.iterator();
            Class<? extends AbstractStructMethodBinding> allBindingsType = iBinding.next().getClass();
            while (iBinding.hasNext()) {
                Class<? extends AbstractStructMethodBinding> bindingType = iBinding.next().getClass();
                if (!bindingType.equals(allBindingsType)) {
                    throw new IllegalArgumentException(String.format("The accessor methods belonging to property '%s' should either all have an implementation in the view,"
                        + " be provided all by the default implementation, or they should all be without an implementation completely.",
                        propertyName));
                }
            }
            if (allBindingsType == ManagedPropertyMethodBinding.class) {
                boolean foundGetter = false;
                boolean foundSetter = false;
                EnumMap<PropertyAccessorType, WeaklyTypeReferencingMethod<?, ?>> accessors = Maps.newEnumMap(PropertyAccessorType.class);
                Set<ModelType<?>> potentialPropertyTypes = Sets.newLinkedHashSet();
                for (AbstractStructMethodBinding binding : bindings) {
                    ManagedPropertyMethodBinding propertyBinding = (ManagedPropertyMethodBinding) binding;
                    PropertyAccessorType accessorType = propertyBinding.getAccessorType();
                    if (accessorType == PropertyAccessorType.SETTER) {
                        foundSetter = true;
                    } else {
                        foundGetter = true;
                    }
                    WeaklyTypeReferencingMethod<?, ?> accessor = propertyBinding.getSource();
                    accessors.put(accessorType, accessor);
                    potentialPropertyTypes.add(accessorType.propertyTypeFor(accessor.getMethod()));
                }
                if (foundSetter && !foundGetter) {
                    throw new IllegalArgumentException(String.format("Managed property '%s' must both have an abstract getter as well as a setter.", propertyName));
                }
                Collection<ModelType<?>> convergingPropertyTypes = findConvergingTypes(potentialPropertyTypes);
                if (convergingPropertyTypes.size() != 1) {
                    throw new IllegalArgumentException(String.format("Managed property '%s' must have a consistent type, but it's defined as %s.", propertyName,
                        Joiner.on(", ").join(ModelType.getDisplayNames(convergingPropertyTypes))));
                }
                ModelType<?> propertyType = Iterables.getOnlyElement(convergingPropertyTypes);
                ManagedProperty<?> managedProperty = createProperty(propertyName, propertyType, !publicSchema.hasProperty(propertyName), accessors);
                generatedPropertiesBuilder.put(propertyName, managedProperty);
            }
        }

        return new DefaultStructBindings<T>(
            publicStructSchema,
            internalViewSchemas,
            delegateSchema,
            generatedPropertiesBuilder.build(),
            viewBindingsBuilder.build(),
            delegateBindingsBuilder.build()
        );
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

    private static <T> ManagedProperty<T> createProperty(String propertyName, ModelType<T> propertyType, boolean internal, Map<PropertyAccessorType, WeaklyTypeReferencingMethod<?, ?>> accessors) {
        return new ManagedProperty<T>(propertyName, propertyType, internal, accessors);
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
