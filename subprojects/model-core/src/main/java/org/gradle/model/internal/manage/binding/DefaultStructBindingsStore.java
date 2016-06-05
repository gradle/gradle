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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.Named;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types.TypeVisitor;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.CollectionSchema;
import org.gradle.model.internal.manage.schema.ManagedImplSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.RuleSourceSchema;
import org.gradle.model.internal.manage.schema.ScalarCollectionSchema;
import org.gradle.model.internal.manage.schema.ScalarValueSchema;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.gradle.internal.reflect.Methods.DESCRIPTOR_EQUIVALENCE;
import static org.gradle.internal.reflect.Methods.SIGNATURE_EQUIVALENCE;
import static org.gradle.internal.reflect.PropertyAccessorType.*;
import static org.gradle.internal.reflect.Types.walkTypeHierarchy;

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
            throw new InvalidManagedTypeException(String.format("Type '%s' is not a valid managed type: delegate type must be null or a non-abstract type instead of '%s'.",
                publicType.getDisplayName(), delegateType.getDisplayName()));
        }

        Set<ModelType<?>> implementedViews = collectImplementedViews(publicType, internalViewTypes, delegateType);
        StructSchema<T> publicSchema = getStructSchema(publicType);
        Iterable<StructSchema<?>> declaredViewSchemas = getStructSchemas(Iterables.concat(Collections.singleton(publicType), internalViewTypes));
        Iterable<StructSchema<?>> implementedSchemas = getStructSchemas(implementedViews);
        StructSchema<D> delegateSchema = delegateType == null ? null : getStructSchema(delegateType);

        StructBindingExtractionContext<T> extractionContext = new StructBindingExtractionContext<T>(publicSchema, implementedSchemas, delegateSchema);

        if (!(publicSchema instanceof RuleSourceSchema)) {
            validateTypeHierarchy(extractionContext, publicType);
            for (ModelType<?> internalViewType : internalViewTypes) {
                validateTypeHierarchy(extractionContext, internalViewType);
            }
        }

        Map<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyBindings = Maps.newTreeMap();
        Set<StructMethodBinding> methodBindings = collectMethodBindings(extractionContext, propertyBindings);
        ImmutableSortedMap<String, ManagedProperty<?>> managedProperties = collectManagedProperties(extractionContext, propertyBindings);

        if (extractionContext.problems.hasProblems()) {
            throw new InvalidManagedTypeException(extractionContext.problems.format());
        }

        return new DefaultStructBindings<T>(
            publicSchema, declaredViewSchemas, implementedSchemas, delegateSchema,
            managedProperties, methodBindings
        );
    }

    private static <T> void validateTypeHierarchy(final StructBindingValidationProblemCollector problems, ModelType<T> type) {
        walkTypeHierarchy(type.getConcreteClass(), new TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                if (type.isAnnotationPresent(Managed.class)) {
                    validateManagedType(problems, type);
                }
                validateType(problems, type);
            }
        });
    }

    private static void validateManagedType(StructBindingValidationProblemCollector problems, Class<?> typeClass) {
        if (!typeClass.isInterface() && !Modifier.isAbstract(typeClass.getModifiers())) {
            problems.add("Must be defined as an interface or an abstract class.");
        }

        if (typeClass.getTypeParameters().length > 0) {
            problems.add("Cannot be a parameterized type.");
        }
    }

    private static void validateType(StructBindingValidationProblemCollector problems, Class<?> typeClass) {
        Constructor<?> customConstructor = findCustomConstructor(typeClass);
        if (customConstructor != null) {
            problems.add(customConstructor, "Custom constructors are not supported.");
        }

        ensureNoInstanceScopedFields(problems, typeClass);

        // sort for determinism
        Method[] methods = typeClass.getDeclaredMethods();
        Arrays.sort(methods, Ordering.usingToString());

        ensureNoProtectedOrPrivateMethods(problems, methods);
        ensureNoDefaultMethods(problems, typeClass, methods);
    }

    private static Constructor<?> findCustomConstructor(Class<?> typeClass) {
        Constructor<?>[] constructors = typeClass.getConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterTypes().length > 0) {
                return constructor;
            }
        }
        return null;
    }

    private static void ensureNoInstanceScopedFields(StructBindingValidationProblemCollector problems, Class<?> typeClass) {
        List<Field> declaredFields = Arrays.asList(typeClass.getDeclaredFields());
        for (Field field : declaredFields) {
            int fieldModifiers = field.getModifiers();
            if (!field.isSynthetic() && !(Modifier.isStatic(fieldModifiers) && Modifier.isFinal(fieldModifiers))) {
                problems.add(field, "Fields must be static final.");
            }
        }
    }

    private static void ensureNoProtectedOrPrivateMethods(StructBindingValidationProblemCollector problems, Method[] declaredMethods) {
        for (Method declaredMethod : declaredMethods) {
            int modifiers = declaredMethod.getModifiers();
            if (!declaredMethod.isSynthetic() && !Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                problems.add(declaredMethod, "Protected and private methods are not supported.");
            }
        }
    }

    private static void ensureNoDefaultMethods(StructBindingValidationProblemCollector problems, Class<?> typeClass, Method[] declaredMethods) {
        if (!typeClass.isInterface()) {
            return;
        }
        for (Method declaredMethod : declaredMethods) {
            if (isDefaultInterfaceMethod(declaredMethod) && PropertyAccessorType.of(declaredMethod) == null) {
                problems.add(declaredMethod, "Default interface methods are only supported for getters and setters.");
            }
        }
    }

    // Copied from Method.isDefault()
    private static boolean isDefaultInterfaceMethod(Method method) {
        // Default methods are public non-abstract instance methods declared in an interface.
        return (method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC;
    }

    private <T> ImmutableSortedMap<String, ManagedProperty<?>> collectManagedProperties(StructBindingExtractionContext<T> extractionContext, Map<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyBindings) {
        ImmutableSortedMap.Builder<String, ManagedProperty<?>> managedPropertiesBuilder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyEntry : propertyBindings.entrySet()) {
            String propertyName = propertyEntry.getKey();
            Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings = propertyEntry.getValue();

            if (isManagedProperty(extractionContext, propertyName, accessorBindings)) {
                if (hasSetter(accessorBindings.keySet()) && !hasGetter(accessorBindings.keySet())) {
                    extractionContext.add(propertyName, "it must both have an abstract getter and a setter");
                    continue;
                }

                ModelType<?> propertyType = determineManagedPropertyType(extractionContext, propertyName, accessorBindings);
                ModelSchema<?> propertySchema = schemaStore.getSchema(propertyType);
                managedPropertiesBuilder.put(propertyName, createManagedProperty(extractionContext, propertyName, propertySchema, accessorBindings));
            }
        }
        return managedPropertiesBuilder.build();
    }

    private static boolean isManagedProperty(StructBindingExtractionContext<?> extractionContext, String propertyName, Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings) {
        Boolean managed = null;
        for (Map.Entry<PropertyAccessorType, Collection<StructMethodBinding>> accessorEntry : accessorBindings.asMap().entrySet()) {
            Collection<StructMethodBinding> bindings = accessorEntry.getValue();
            boolean managedPropertyAccessor = isManagedPropertyAccessor(extractionContext, propertyName, bindings);
            if (managed == null) {
                managed = managedPropertyAccessor;
            } else if (managed != managedPropertyAccessor) {
                extractionContext.add(propertyName, "it must have either only abstract accessor methods or only implemented accessor methods");
                managed = false;
                break;
            }
        }
        assert managed != null;
        return managed;
    }

    private static boolean isManagedPropertyAccessor(StructBindingExtractionContext<?> extractionContext, String propertyName, Collection<StructMethodBinding> bindings) {
        Set<WeaklyTypeReferencingMethod<?, ?>> implMethods = Sets.newLinkedHashSet();
        for (StructMethodBinding binding : bindings) {
            if (binding instanceof StructMethodImplementationBinding) {
                implMethods.add(((StructMethodImplementationBinding) binding).getImplementorMethod());
            }
        }
        switch (implMethods.size()) {
            case 0:
                return true;
            case 1:
                return false;
            default:
                extractionContext.add(propertyName, String.format("it has multiple implementations for accessor method: %s",
                    Joiner.on(", ").join(implMethods)));
                return false;
        }
    }

    private static ModelType<?> determineManagedPropertyType(StructBindingExtractionContext<?> extractionContext, String propertyName, Multimap<PropertyAccessorType, StructMethodBinding> accessorBindings) {
        Collection<StructMethodBinding> isGetter = accessorBindings.get(IS_GETTER);
        for (StructMethodBinding isGetterBinding : isGetter) {
            if (!((ManagedPropertyMethodBinding) isGetterBinding).getDeclaredPropertyType().equals(ModelType.of(Boolean.TYPE))) {
                WeaklyTypeReferencingMethod<?, ?> isGetterMethod = isGetterBinding.getViewMethod();
                extractionContext.add(isGetterMethod, String.format("it should either return 'boolean', or its name should be '%s()'",
                    "get" + isGetterMethod.getName().substring(2)));
            }
        }
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
            extractionContext.add(propertyName, String.format("it must have a consistent type, but it's defined as %s",
                Joiner.on(", ").join(ModelTypes.getDisplayNames(convergingPropertyTypes))));
            return convergingPropertyTypes.iterator().next();
        }
        ModelType<?> propertyType = Iterables.getOnlyElement(convergingPropertyTypes);

        for (StructMethodBinding setterBinding : accessorBindings.get(SETTER)) {
            ManagedPropertyMethodBinding propertySetterBinding = (ManagedPropertyMethodBinding) setterBinding;
            ModelType<?> declaredSetterType = propertySetterBinding.getDeclaredPropertyType();
            if (!declaredSetterType.equals(propertyType)) {
                extractionContext.add(setterBinding.getViewMethod(), String.format("it should take parameter with type '%s'",
                    propertyType.getDisplayName()));
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
            walkTypeHierarchy(delegateType.getConcreteClass(), new TypeVisitor<D>() {
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

    private static <T> Set<StructMethodBinding> collectMethodBindings(StructBindingExtractionContext<T> extractionContext, Map<String, Multimap<PropertyAccessorType, StructMethodBinding>> propertyBindings) {
        Collection<WeaklyTypeReferencingMethod<?, ?>> implementedMethods = collectImplementedMethods(extractionContext.getImplementedSchemas());
        Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> publicViewImplMethods = collectPublicViewImplMethods(extractionContext.getPublicSchema());
        Map<Wrapper<Method>, WeaklyTypeReferencingMethod<?, ?>> delegateMethods = collectDelegateMethods(extractionContext.getDelegateSchema());

        ImmutableSet.Builder<StructMethodBinding> methodBindingsBuilder = ImmutableSet.builder();
        for (WeaklyTypeReferencingMethod<?, ?> weakImplementedMethod : implementedMethods) {
            Method implementedMethod = weakImplementedMethod.getMethod();
            PropertyAccessorType accessorType = PropertyAccessorType.of(implementedMethod);

            Wrapper<Method> methodKey = SIGNATURE_EQUIVALENCE.wrap(implementedMethod);
            WeaklyTypeReferencingMethod<?, ?> weakDelegateImplMethod = delegateMethods.get(methodKey);
            WeaklyTypeReferencingMethod<?, ?> weakPublicImplMethod = publicViewImplMethods.get(methodKey);
            if (weakDelegateImplMethod != null && weakPublicImplMethod != null) {
                extractionContext.add(weakImplementedMethod, String.format("it is both implemented by the view '%s' and the delegate type '%s'",
                    extractionContext.getPublicSchema().getType().getDisplayName(),
                    extractionContext.getDelegateSchema().getType().getDisplayName()));
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
                handleNoMethodImplementation(extractionContext, weakImplementedMethod);
                continue;
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

    private static void handleNoMethodImplementation(StructBindingValidationProblemCollector problems, WeaklyTypeReferencingMethod<?, ?> method) {
        String methodName = method.getName();
        PropertyAccessorType accessorType = PropertyAccessorType.fromName(methodName);
        if (accessorType != null) {
            switch (accessorType) {
                case GET_GETTER:
                case IS_GETTER:
                    if (!PropertyAccessorType.takesNoParameter(method.getMethod())) {
                        problems.add(method, "property accessor", "getter method must not take parameters");
                    }
                    break;
                case SETTER:
                    if (!hasVoidReturnType(method.getMethod())) {
                        problems.add(method, "property accessor", "setter method must have void return type");
                    }
                    if (!takesSingleParameter(method.getMethod())) {
                        problems.add(method, "property accessor", "setter method must take exactly one parameter");
                    }
                    break;
                default:
                    throw new AssertionError();
            }
        } else {
            problems.add(method, "managed type", "it must have an implementation");
        }
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

    private static <T> ManagedProperty<T> createManagedProperty(StructBindingExtractionContext<?> extractionContext, String propertyName, ModelSchema<T> propertySchema, Multimap<PropertyAccessorType, StructMethodBinding> accessors) {
        boolean writable = accessors.containsKey(SETTER);
        boolean declaredAsUnmanaged = isDeclaredAsHavingUnmanagedType(accessors.get(GET_GETTER))
            || isDeclaredAsHavingUnmanagedType(accessors.get(IS_GETTER));
        boolean internal = !extractionContext.getPublicSchema().hasProperty(propertyName);

        validateManagedProperty(extractionContext, propertyName, propertySchema, writable, declaredAsUnmanaged);

        return new ManagedProperty<T>(propertyName, propertySchema.getType(), writable, declaredAsUnmanaged, internal);
    }

    private static void validateManagedProperty(StructBindingExtractionContext<?> extractionContext, String propertyName, ModelSchema<?> propertySchema, boolean writable, boolean isDeclaredAsHavingUnmanagedType) {
        if (propertyName.equals("name") && Named.class.isAssignableFrom(extractionContext.getPublicSchema().getType().getRawClass())) {
            if (writable) {
                extractionContext.add(propertyName, String.format("it must not have a setter, because the type implements '%s'", Named.class.getName()));
            }
            return;
        }

        // Only managed implementation and value types are allowed as a managed property type unless marked with @Unmanaged
        boolean isAllowedPropertyTypeOfManagedType = propertySchema instanceof ManagedImplSchema
            || propertySchema instanceof ScalarValueSchema;

        ModelType<?> propertyType = propertySchema.getType();

        if (isAllowedPropertyTypeOfManagedType && isDeclaredAsHavingUnmanagedType) {
            extractionContext.add(propertyName, String.format("it is marked as @Unmanaged, but is of @Managed type '%s'; please remove the @Managed annotation",
                    propertyType.getDisplayName()
            ));
        }

        if (!writable && isDeclaredAsHavingUnmanagedType) {
            extractionContext.add(propertyName, "it must not be read only, because it is marked as @Unmanaged");
        }

        if (!(extractionContext.getPublicSchema() instanceof RuleSourceSchema)) {
            if (propertySchema instanceof CollectionSchema) {
                if (!(propertySchema instanceof ScalarCollectionSchema) && writable) {
                    extractionContext.add(propertyName, String.format("it cannot have a setter (%s properties must be read only)",
                        propertyType.getRawClass().getSimpleName()));
                }
            }
        }
    }

    private static boolean isDeclaredAsHavingUnmanagedType(Collection<StructMethodBinding> accessorBindings) {
        for (StructMethodBinding accessorBinding : accessorBindings) {
            if (accessorBinding.getViewMethod().getMethod().isAnnotationPresent(Unmanaged.class)) {
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
