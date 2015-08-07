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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.*;

abstract public class ManagedImplTypeSchemaExtractionStrategySupport extends ImplTypeSchemaExtractionStrategySupport {

    @Override
    protected boolean isTarget(ModelType<?> type) {
        // Every managed class is a struct that hasn't been handled before
        return type.getRawClass().isAnnotationPresent(Managed.class);
    }

    @Override
    @SuppressWarnings("SimplifiableIfStatement")
    protected boolean isGetterDefinedInManagedType(ModelSchemaExtractionContext<?> extractionContext, String methodName, Collection<Method> getterMethods) {
        if (methodName.equals("getName") && Named.class.isAssignableFrom(extractionContext.getType().getRawClass())) {
            return true;
        }
        return super.isGetterDefinedInManagedType(extractionContext, methodName, getterMethods);
    }

    @Override
    protected <P> Action<ModelSchemaExtractionContext<P>> createPropertyValidator(final ModelProperty<P> property, final ModelSchemaCache modelSchemaCache) {
        return new Action<ModelSchemaExtractionContext<P>>() {
            @Override
            public void execute(ModelSchemaExtractionContext<P> propertyExtractionContext) {
                // Do not validate unmanaged properties
                if (!property.isManaged()) {
                    return;
                }

                ModelSchema<P> propertySchema = modelSchemaCache.get(property.getType());
                ModelSchemaExtractionContext<?> parentContext = propertyExtractionContext.getParent();

                if (property.getName().equals("name") && Named.class.isAssignableFrom(parentContext.getType().getRawClass())) {
                    if (property.isWritable()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "@Managed types implementing %s must not declare a setter for the name property",
                            Named.class.getName()
                        ));
                    } else {
                        return;
                    }
                }

                boolean isDeclaredAsHavingUnmanagedType = property.isAnnotationPresent(Unmanaged.class);

                if (propertySchema.getKind().isAllowedPropertyTypeOfManagedType() && isDeclaredAsHavingUnmanagedType) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                        "property '%s' is marked as @Unmanaged, but is of @Managed type '%s'. Please remove the @Managed annotation.%n",
                        property.getName(), property.getType()
                    ));
                }

                if (!propertySchema.getKind().isAllowedPropertyTypeOfManagedType() && !isDeclaredAsHavingUnmanagedType) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                        "type %s cannot be used for property '%s' as it is an unmanaged type (please annotate the getter with @org.gradle.model.Unmanaged if you want this property to be unmanaged).%n%s",
                        property.getType(), property.getName(), ModelSchemaExtractor.getManageablePropertyTypesDescription()
                    ));
                }

                if (!property.isWritable()) {
                    if (isDeclaredAsHavingUnmanagedType) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "unmanaged property '%s' cannot be read only, unmanaged properties must have setters",
                            property.getName())
                        );
                    }

                    if (!propertySchema.getKind().isManaged()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "read only property '%s' has non managed type %s, only managed types can be used",
                            property.getName(), property.getType()));
                    }
                }

                if (propertySchema.getKind() == ModelSchema.Kind.COLLECTION) {
                    if (property.isWritable()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "property '%s' cannot have a setter (%s properties must be read only).",
                            property.getName(), property.getType().toString()));
                    }

                    ModelCollectionSchema<P, ?> propertyCollectionsSchema = (ModelCollectionSchema<P, ?>) propertySchema;

                    ModelType<?> elementType = propertyCollectionsSchema.getElementType();
                    ModelSchema<?> elementTypeSchema = modelSchemaCache.get(elementType);

                    if (!elementTypeSchema.getKind().isManaged()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "property '%s' cannot be a model map of type %s as it is not a %s type.",
                            property.getName(), elementType, Managed.class.getName()
                        ));
                    }
                }
            }
        };
    }

    @Override
    protected void invalidGetterHasParameterTypes(ModelSchemaExtractionContext<?> extractionContext, Method getter) {
        throw invalidMethod(extractionContext, "getter methods cannot take parameters", getter);
    }

    @Override
    protected void invalidGetterNoUppercase(ModelSchemaExtractionContext<?> extractionContext, Method getter) {
        throw invalidMethod(extractionContext, "the 4th character of the getter method name must be an uppercase character", getter);
    }

    @Override
    protected void invalidGetterHasPrimitiveType(ModelSchemaExtractionContext<?> extractionContext, Method getter) {
        throw invalidMethod(extractionContext, "managed properties cannot have primitive types", getter);
    }

    @Override
    protected boolean hasOverloadedMethods(ModelSchemaExtractionContext<?> extractionContext, String methodName, Collection<Method> methods) {
        List<Method> overloadedMethods = getOverloadedMethods(methods);
        if (overloadedMethods != null) {
            // Ignore overloaded methods defined in unmanaged types
            if (isMethodDeclaredInManagedType(methods)) {
                throw invalidMethods(extractionContext, "overloaded methods are not supported", overloadedMethods);
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void validateAllNecessaryMethodsHandled(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> allMethods, final Set<Method> handledMethods) {
        Iterable<Method> notHandled = Iterables.filter(allMethods, new Predicate<Method>() {
            @Override
            public boolean apply(Method method) {
                return method.getDeclaringClass().isAnnotationPresent(Managed.class) && !handledMethods.contains(method);
            }
        });

        // TODO - should call out valid getters without setters
        if (!Iterables.isEmpty(notHandled)) {
            throw invalidMethods(extractionContext, "only paired getter/setter methods are supported", notHandled);
        }
    }

    @Override
    protected <R> void validateTypeHierarchy(final ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type) {
        walkTypeHierarchy(type.getConcreteClass(), new ModelSchemaUtils.TypeVisitor() {
            @Override
            public void visitType(Class<?> type) {
                if (type.isAnnotationPresent(Managed.class)) {
                    visitManagedType(extractionContext, type);
                }
            }
        });
    }

    private void visitManagedType(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        if (!typeClass.isInterface() && !Modifier.isAbstract(typeClass.getModifiers())) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "must be defined as an interface or an abstract class.");
        }

        if (typeClass.getTypeParameters().length > 0) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "cannot be a parameterized type.");
        }

        Constructor<?> customConstructor = findCustomConstructor(typeClass);
        if (customConstructor != null) {
            throw invalidMethod(extractionContext, "custom constructors are not allowed", customConstructor);
        }

        ensureNoInstanceScopedFields(extractionContext, typeClass);
        ensureNoProtectedOrPrivateMethods(extractionContext, typeClass);
    }

    private void ensureNoProtectedOrPrivateMethods(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        Class<?> superClass = typeClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            ensureNoProtectedOrPrivateMethods(extractionContext, superClass);
        }

        Iterable<Method> protectedAndPrivateMethods = Iterables.filter(Arrays.asList(typeClass.getDeclaredMethods()), new Predicate<Method>() {
            @Override
            public boolean apply(Method method) {
                int modifiers = method.getModifiers();
                return !method.isSynthetic() && (Modifier.isProtected(modifiers) || Modifier.isPrivate(modifiers));
            }
        });

        if (!Iterables.isEmpty(protectedAndPrivateMethods)) {
            throw invalidMethods(extractionContext, "protected and private methods are not allowed", protectedAndPrivateMethods);
        }
    }

    private void ensureNoInstanceScopedFields(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        Class<?> superClass = typeClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            ensureNoInstanceScopedFields(extractionContext, superClass);
        }

        List<Field> declaredFields = Arrays.asList(typeClass.getDeclaredFields());
        Iterable<Field> instanceScopedFields = Iterables.filter(declaredFields, new Predicate<Field>() {
            public boolean apply(Field field) {
                return !Modifier.isStatic(field.getModifiers()) && !field.getName().equals("metaClass");
            }
        });
        ImmutableSortedSet<String> sortedDescriptions = ImmutableSortedSet.copyOf(Iterables.transform(instanceScopedFields, new Function<Field, String>() {
            public String apply(Field field) {
                return field.toString();
            }
        }));
        if (!sortedDescriptions.isEmpty()) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "instance scoped fields are not allowed (found fields: " + Joiner.on(", ").join(sortedDescriptions) + ").");
        }
    }

    private Constructor<?> findCustomConstructor(Class<?> typeClass) {
        Class<?> superClass = typeClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            Constructor<?> customSuperConstructor = findCustomConstructor(typeClass.getSuperclass());
            if (customSuperConstructor != null) {
                return customSuperConstructor;
            }
        }
        Constructor<?>[] constructors = typeClass.getConstructors();
        if (constructors.length == 0 || (constructors.length == 1 && constructors[0].getParameterTypes().length == 0)) {
            return null;
        } else {
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterTypes().length > 0) {
                    return constructor;
                }
            }
            //this should never happen
            throw new RuntimeException(String.format("Expected a constructor taking at least one argument in %s but no such constructors were found", typeClass.getName()));
        }
    }
}
