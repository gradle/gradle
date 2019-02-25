/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.reflect;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.gradle.internal.reflect.Methods.SIGNATURE_EQUIVALENCE;

public class PropertyExtractor {

    private final String displayName;
    private final Set<Class<? extends Annotation>> primaryAnnotationTypes;
    private final Set<Class<? extends Annotation>> relevantAnnotationTypes;
    private final Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides;
    private final Set<Class<? extends Annotation>> otherKnownAnnotations;
    private final Set<Class<?>> ignoredSuperclasses;
    private final Set<Equivalence.Wrapper<Method>> ignoredMethods;

    public PropertyExtractor(String displayName, Set<Class<? extends Annotation>> primaryAnnotationTypes, Set<Class<? extends Annotation>> relevantAnnotationTypes, Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides, Set<Class<? extends Annotation>> otherKnownAnnotations, Set<Class<?>> ignoredSuperclasses, Set<Class<?>> ignoreMethodsFromClasses) {
        this.displayName = displayName;
        this.primaryAnnotationTypes = primaryAnnotationTypes;
        this.relevantAnnotationTypes = relevantAnnotationTypes;
        this.annotationOverrides = annotationOverrides;
        this.otherKnownAnnotations = otherKnownAnnotations;
        this.ignoredSuperclasses = ignoredSuperclasses;
        this.ignoredMethods = allMethodsOf(ignoreMethodsFromClasses);
    }

    private static ImmutableSet<Equivalence.Wrapper<Method>> allMethodsOf(Iterable<Class<?>> classes) {
        List<Equivalence.Wrapper<Method>> methods = Lists.newArrayList();
        for (Class<?> clazz : classes) {
            for (Method method : clazz.getMethods()) {
                methods.add(SIGNATURE_EQUIVALENCE.wrap(method));
            }
        }
        return ImmutableSet.copyOf(methods);
    }

    public <T> ImmutableSet<PropertyMetadata> extractPropertyMetadata(Class<T> type, ParameterValidationContext validationContext) {
        final Set<Class<? extends Annotation>> propertyTypeAnnotations = primaryAnnotationTypes;
        final Map<String, PropertyMetadataBuilder> properties = Maps.newLinkedHashMap();
        Types.walkTypeHierarchy(type, ignoredSuperclasses, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                if (type.isSynthetic()) {
                    return;
                }
                Map<String, Field> fields = getFields(type);
                List<Getter> getters = getGetters(type);
                for (Getter getter : getters) {
                    Method method = getter.getMethod();
                    if (method.isSynthetic()) {
                        continue;
                    }
                    String fieldName = getter.getName();
                    Field field = fields.get(fieldName);
                    if (field != null && field.isSynthetic()) {
                        continue;
                    }

                    PropertyMetadataBuilder propertyMetadata = properties.get(fieldName);
                    if (propertyMetadata == null) {
                        propertyMetadata = new PropertyMetadataBuilder(propertyTypeAnnotations, fieldName, method);
                        properties.put(fieldName, propertyMetadata);
                    }

                    Iterable<Annotation> declaredAnnotations = mergeDeclaredAnnotations(method, field, propertyMetadata, validationContext);

                    // Discard overridden property type annotations when an overriding annotation is also present
                    Iterable<Annotation> overriddenAnnotations = filterOverridingAnnotations(declaredAnnotations, propertyTypeAnnotations);

                    recordAnnotations(propertyMetadata, overriddenAnnotations, declaredAnnotations, propertyTypeAnnotations, validationContext);
                }
            }
        });

        ImmutableSet.Builder<PropertyMetadata> propertyBuilder = ImmutableSet.builderWithExpectedSize(properties.size());
        for (PropertyMetadataBuilder property : properties.values()) {
            validateProperty(property, validationContext);
            if (property.propertyType != null) {
                propertyBuilder.add(property.toMetadata());
            }
        }
        return propertyBuilder.build();
    }

    private void validateProperty(PropertyMetadataBuilder property, ParameterValidationContext validationContext) {
        if (!property.brokenType && property.propertyType == null) {
            validationContext.visitError(null, property.fieldName, "is not annotated with " + displayName);
        }
    }

    private Iterable<Annotation> mergeDeclaredAnnotations(Method method, @Nullable Field field, PropertyMetadataBuilder property, ParameterValidationContext validationContext) {
        Collection<Annotation> methodAnnotations = collectRelevantAnnotations(method.getDeclaredAnnotations(), property, validationContext);
        if (Modifier.isPrivate(method.getModifiers())) {
            if (!methodAnnotations.isEmpty()) {
                validationContext.visitError(null, property.fieldName, "is private and annotated with @" + methodAnnotations.iterator().next().annotationType().getSimpleName());
            }
            property.hasBrokenType();
        }
        if (field == null) {
            return methodAnnotations;
        }
        Collection<Annotation> fieldAnnotations = collectRelevantAnnotations(field.getDeclaredAnnotations(), property, validationContext);
        if (fieldAnnotations.isEmpty()) {
            return methodAnnotations;
        }
        if (methodAnnotations.isEmpty()) {
            return fieldAnnotations;
        }

        for (Annotation methodAnnotation : methodAnnotations) {
            Iterator<Annotation> iFieldAnnotation = fieldAnnotations.iterator();
            while (iFieldAnnotation.hasNext()) {
                Annotation fieldAnnotation = iFieldAnnotation.next();
                if (methodAnnotation.annotationType().equals(fieldAnnotation.annotationType())) {
                    validationContext.visitError(null, property.fieldName, "has both a getter and field declared with annotation @" + methodAnnotation.annotationType().getSimpleName());
                    iFieldAnnotation.remove();
                }
            }
        }

        return Iterables.concat(methodAnnotations, fieldAnnotations);
    }

    private Iterable<Annotation> filterOverridingAnnotations(final Iterable<Annotation> declaredAnnotations, final Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        return Iterables.filter(declaredAnnotations, new Predicate<Annotation>() {
            @Override
            public boolean apply(Annotation input) {
                Class<? extends Annotation> annotationType = input.annotationType();
                if (!propertyTypeAnnotations.contains(annotationType)) {
                    return true;
                }
                for (Class<? extends Annotation> overridingAnnotation : annotationOverrides.get(annotationType)) {
                    for (Annotation declaredAnnotation : declaredAnnotations) {
                        if (declaredAnnotation.annotationType().equals(overridingAnnotation)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        });
    }

    private void recordAnnotations(PropertyMetadataBuilder property, Iterable<Annotation> overriddenAnnotations, Iterable<Annotation> annotations, Set<Class<? extends Annotation>> propertyTypeAnnotations, ParameterValidationContext validationContext) {
        Set<Class<? extends Annotation>> declaredPropertyTypes = Sets.newLinkedHashSet();
        for (Annotation annotation : overriddenAnnotations) {
            if (propertyTypeAnnotations.contains(annotation.annotationType())) {
                declaredPropertyTypes.add(annotation.annotationType());
                property.addAnnotation(annotation);
            }
        }

        for (Annotation annotation : annotations) {
            property.addAnnotation(annotation);
        }

        if (declaredPropertyTypes.size() > 1) {
            Iterable<String> names = Iterables.transform(declaredPropertyTypes, new Function<Class<? extends Annotation>, String>() {
                @Override
                public String apply(Class<? extends Annotation> annotationType) {
                    return "@" + annotationType.getSimpleName();
                }
            });
            Set<String> sortedNames = CollectionUtils.addAll(new TreeSet<String>(), names);
            validationContext.visitError(null, property.fieldName, "has conflicting property types declared: " + Joiner.on(", ").join(sortedNames));
        }
    }

    private Collection<Annotation> collectRelevantAnnotations(Annotation[] annotations, PropertyMetadataBuilder property, ParameterValidationContext validationContext) {
        List<Annotation> relevantAnnotations = Lists.newArrayListWithCapacity(annotations.length);
        for (Annotation annotation : annotations) {
            if (relevantAnnotationTypes.contains(annotation.annotationType())) {
                relevantAnnotations.add(annotation);
            }
            if (otherKnownAnnotations.contains(annotation.annotationType())) {
                validationContext.visitError(null, property.fieldName, "is annotated with unsupported annotation @" + annotation.annotationType().getSimpleName());
                property.hasBrokenType();
            }
        }
        return relevantAnnotations;
    }

    private static Map<String, Field> getFields(Class<?> type) {
        Map<String, Field> fields = Maps.newHashMap();
        for (Field field : type.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
        return fields;
    }

    private List<Getter> getGetters(Class<?> type) {
        Method[] methods = type.getDeclaredMethods();
        List<Getter> getters = Lists.newArrayListWithCapacity(methods.length);
        for (Method method : methods) {
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            // We only care about getters
            if (accessorType == null || accessorType == PropertyAccessorType.SETTER) {
                continue;
            }
            // We only care about actual methods the user added
            if (method.isBridge() || ignoredMethods.contains(SIGNATURE_EQUIVALENCE.wrap(method))) {
                continue;
            }
            getters.add(new Getter(method, accessorType.propertyNameFor(method)));
        }
        Collections.sort(getters);
        return getters;
    }

    private static class Getter implements Comparable<Getter> {
        private final Method method;
        private final String name;

        Getter(Method method, String name) {
            this.method = method;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Method getMethod() {
            return method;
        }

        @Override
        public int compareTo(@Nonnull Getter o) {
            // Sort "is"-getters before "get"-getters when both are available
            return method.getName().compareTo(o.method.getName());
        }
    }

    private static class PropertyMetadataBuilder {
        private final Set<Class<? extends Annotation>> propertyTypeAnnotations;
        private final String fieldName;
        private final Method method;
        private Class<? extends Annotation> propertyType;
        private final Map<Class<? extends Annotation>, Annotation> annotations = Maps.newHashMap();
        private boolean brokenType;

        PropertyMetadataBuilder(Set<Class<? extends Annotation>> propertyTypeAnnotations, String fieldName, Method method) {
            this.propertyTypeAnnotations = propertyTypeAnnotations;
            this.fieldName = fieldName;
            this.method = method;
        }

        public void hasBrokenType() {
            this.brokenType = true;
        }

        public void addAnnotation(Annotation annotation) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            // Record the most specific property type annotation only
            if (propertyType == null && isPropertyTypeAnnotation(annotationType)) {
                propertyType = annotationType;
            }
            // Record the most specific annotation only
            if (!isAnnotationPresent(annotation.annotationType())) {
                annotations.put(annotationType, annotation);
            }
        }

        boolean isPropertyTypeAnnotation(Class<? extends Annotation> annotationType) {
            return propertyTypeAnnotations.contains(annotationType);
        }

        boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return getAnnotation(annotationType) != null;
        }

        @Nullable
        <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return annotationType.cast(annotations.get(annotationType));
        }

        PropertyMetadata toMetadata() {
            return new DefaultPropertyMetadata(fieldName, method, propertyType, ImmutableMap.copyOf(annotations));
        }
    }

    private static class DefaultPropertyMetadata implements PropertyMetadata {
        private final String fieldName;
        private final Method method;
        private final Class<? extends Annotation> propertyType;
        private final ImmutableMap<Class<? extends Annotation>, Annotation> annotations;

        DefaultPropertyMetadata(String fieldName, Method method, Class<? extends Annotation> propertyType, ImmutableMap<Class<? extends Annotation>, Annotation> annotations) {
            this.fieldName = fieldName;
            this.method = method;
            this.propertyType = propertyType;
            this.annotations = annotations;
        }

        @Override
        public String getPropertyName() {
            return fieldName;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return getAnnotation(annotationType) != null;
        }

        @Override
        @Nullable
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return annotationType.cast(annotations.get(annotationType));
        }

        @Override
        @Nullable
        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        @Override
        public Class<?> getDeclaredType() {
            return method.getReturnType();
        }

        @Override
        public Method getGetterMethod() {
            return method;
        }
    }

}
