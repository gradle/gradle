/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.tasks.properties.annotations.DestroysPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.InputPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.LocalStatePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.NestedBeanAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.NoOpPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoriesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoryPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilePropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputFilesPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OverridingPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;
import org.gradle.internal.scripts.ScriptOrigin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
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

public class DefaultPropertyMetadataStore implements PropertyMetadataStore {
    // Avoid reflecting on classes we know we don't need to look at
    @SuppressWarnings("RedundantTypeArguments")
    private static final Collection<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.<Class<?>>of(
        ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class, IConventionAware.class, ExtensionAware.class, HasConvention.class, ScriptOrigin.class, DynamicObjectAware.class
    );

    private final static List<? extends PropertyAnnotationHandler> HANDLERS = Arrays.asList(
        new InputFilePropertyAnnotationHandler(),
        new InputDirectoryPropertyAnnotationHandler(),
        new InputFilesPropertyAnnotationHandler(),
        new OutputFilePropertyAnnotationHandler(),
        new OutputFilesPropertyAnnotationHandler(),
        new OutputDirectoryPropertyAnnotationHandler(),
        new OutputDirectoriesPropertyAnnotationHandler(),
        new InputPropertyAnnotationHandler(),
        new DestroysPropertyAnnotationHandler(),
        new LocalStatePropertyAnnotationHandler(),
        new NestedBeanAnnotationHandler(),
        new NoOpPropertyAnnotationHandler(Inject.class),
        new NoOpPropertyAnnotationHandler(Console.class),
        new NoOpPropertyAnnotationHandler(Internal.class),
        new NoOpPropertyAnnotationHandler(OptionValues.class)
    );

    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers;
    private final Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides;
    private final Set<Class<? extends Annotation>> relevantAnnotationTypes;
    private final CrossBuildInMemoryCache<Class<?>, TypeMetadata> cache;
    private Transformer<TypeMetadata, Class<?>> typeMetadataFactory = new Transformer<TypeMetadata, Class<?>>() {
        @Override
        public TypeMetadata transform(Class<?> type) {
            return createTypeMetadata(type);
        }
    };

    public DefaultPropertyMetadataStore(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers, CrossBuildInMemoryCacheFactory cacheFactory) {
        Iterable<PropertyAnnotationHandler> allAnnotationHandlers = Iterables.concat(HANDLERS, customAnnotationHandlers);
        Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationsHandlers = Maps.uniqueIndex(allAnnotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        });
        this.annotationHandlers = annotationsHandlers;
        this.annotationOverrides = collectAnnotationOverrides(allAnnotationHandlers);
        this.relevantAnnotationTypes = collectRelevantAnnotationTypes(annotationsHandlers.keySet());
        cache = cacheFactory.newClassCache();
    }

    private static Multimap<Class<? extends Annotation>, Class<? extends Annotation>> collectAnnotationOverrides(Iterable<PropertyAnnotationHandler> allAnnotationHandlers) {
        ImmutableSetMultimap.Builder<Class<? extends Annotation>, Class<? extends Annotation>> builder = ImmutableSetMultimap.builder();
        for (PropertyAnnotationHandler handler : allAnnotationHandlers) {
            if (handler instanceof OverridingPropertyAnnotationHandler) {
                builder.put(((OverridingPropertyAnnotationHandler) handler).getOverriddenAnnotationType(), handler.getAnnotationType());
            }
        }
        return builder.build();
    }

    private static Set<Class<? extends Annotation>> collectRelevantAnnotationTypes(Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        return ImmutableSet.<Class<? extends Annotation>>builder()
            .addAll(propertyTypeAnnotations)
            .add(Optional.class)
            .add(SkipWhenEmpty.class)
            .add(PathSensitive.class)
            .build();
    }

    @Override
    public <T> TypeMetadata getTypeMetadata(final Class<T> type) {
        return cache.get(type, typeMetadataFactory);
    }

    private <T> TypeMetadata createTypeMetadata(Class<T> type) {
        final Set<Class<? extends Annotation>> propertyTypeAnnotations = annotationHandlers.keySet();
        final Map<String, PropertyMetadataBuilder> properties = Maps.newLinkedHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
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
                    if (method.getName().equals("getContentHash") || method.getName().equals("getOriginalClassName")) {
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

                    Iterable<Annotation> declaredAnnotations = mergeDeclaredAnnotations(method, field, propertyMetadata);

                    // Discard overridden property type annotations when an overriding annotation is also present
                    Iterable<Annotation> overriddenAnnotations = filterOverridingAnnotations(declaredAnnotations, propertyTypeAnnotations);

                    recordAnnotations(propertyMetadata, overriddenAnnotations, propertyTypeAnnotations);
                }
            }
        });
        ImmutableSet.Builder<PropertyMetadata> builder = ImmutableSet.builder();
        for (PropertyMetadataBuilder property : properties.values()) {
            builder.add(property.toMetadata());
        }
        return new DefaultTypeMetadata(builder.build());
    }

    private Iterable<Annotation> mergeDeclaredAnnotations(Method method, @Nullable Field field, PropertyMetadataBuilder property) {
        Collection<Annotation> methodAnnotations = collectRelevantAnnotations(method.getDeclaredAnnotations());
        if (Modifier.isPrivate(method.getModifiers()) && !methodAnnotations.isEmpty()) {
            property.validationMessage("is private and annotated with an input or output annotation");
        }
        if (field == null) {
            return methodAnnotations;
        }
        Collection<Annotation> fieldAnnotations = collectRelevantAnnotations(field.getDeclaredAnnotations());
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
                    property.validationMessage("has both a getter and field declared with annotation @" + methodAnnotation.annotationType().getSimpleName());
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

    private void recordAnnotations(PropertyMetadataBuilder property, Iterable<Annotation> annotations, Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        Set<Class<? extends Annotation>> declaredPropertyTypes = Sets.newLinkedHashSet();
        for (Annotation annotation : annotations) {
            if (propertyTypeAnnotations.contains(annotation.annotationType())) {
                declaredPropertyTypes.add(annotation.annotationType());
            }
            property.addAnnotation(annotation);
        }

        if (declaredPropertyTypes.size() > 1) {
            property.validationMessage("has conflicting property types declared: "
                    + Joiner.on(", ").join(Iterables.transform(declaredPropertyTypes, new Function<Class<? extends Annotation>, String>() {
                    @Override
                    public String apply(Class<? extends Annotation> annotationType) {
                        return "@" + annotationType.getSimpleName();
                    }
                }))
            );
        }
    }


    private Collection<Annotation> collectRelevantAnnotations(Annotation[] annotations) {
        List<Annotation> relevantAnnotations = Lists.newArrayListWithCapacity(annotations.length);
        for (Annotation annotation : annotations) {
            if (relevantAnnotationTypes.contains(annotation.annotationType())) {
                relevantAnnotations.add(annotation);
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

    private static List<Getter> getGetters(Class<?> type) {
        Method[] methods = type.getDeclaredMethods();
        List<Getter> getters = Lists.newArrayListWithCapacity(methods.length);
        for (Method method : methods) {
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            // We only care about getters
            if (accessorType == null || accessorType == PropertyAccessorType.SETTER) {
                continue;
            }
            // We only care about actual methods the user added
            if (method.isBridge() || GroovyMethods.isObjectMethod(method)) {
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

    private class DefaultTypeMetadata implements TypeMetadata {
        private final ImmutableSet<PropertyMetadata> propertiesMetadata;

        DefaultTypeMetadata(ImmutableSet<PropertyMetadata> propertiesMetadata) {
            this.propertiesMetadata = propertiesMetadata;
        }

        @Override
        public Set<PropertyMetadata> getPropertiesMetadata() {
            return propertiesMetadata;
        }

        @Override
        public boolean hasAnnotatedProperties() {
            for (PropertyMetadata metadata : propertiesMetadata) {
                if (metadata.getPropertyType() != null) {
                    return true;
                }
            }
            return false;
        }
    }

    private class PropertyMetadataBuilder {
        private final Set<Class<? extends Annotation>> propertyTypeAnnotations;
        private final String fieldName;
        private final Method method;
        private Class<? extends Annotation> propertyType;
        private final Map<Class<? extends Annotation>, Annotation> annotations = Maps.newHashMap();
        private final List<String> validationMessages = Lists.newArrayList();

        PropertyMetadataBuilder(Set<Class<? extends Annotation>> propertyTypeAnnotations, String fieldName, Method method) {
            this.propertyTypeAnnotations = propertyTypeAnnotations;
            this.fieldName = fieldName;
            this.method = method;
        }

        public void validationMessage(String message) {
            validationMessages.add(message);
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
            return new DefaultPropertyMetadata(fieldName, method, propertyType, annotationHandlers.get(propertyType), ImmutableMap.copyOf(annotations), ImmutableList.copyOf(validationMessages));
        }
    }

    private static class DefaultPropertyMetadata implements PropertyMetadata {
        private final String fieldName;
        private final Method method;
        private final Class<? extends Annotation> propertyType;
        private final PropertyAnnotationHandler annotationHandler;
        private final ImmutableMap<Class<? extends Annotation>, Annotation> annotations;
        private final ImmutableList<String> validationMessages;

        DefaultPropertyMetadata(String fieldName, Method method, Class<? extends Annotation> propertyType, PropertyAnnotationHandler annotationHandler, ImmutableMap<Class<? extends Annotation>, Annotation> annotations, ImmutableList<String> validationMessages) {
            this.fieldName = fieldName;
            this.method = method;
            this.propertyType = propertyType;
            this.annotationHandler = annotationHandler;
            this.annotations = annotations;
            this.validationMessages = validationMessages;
        }

        @Override
        public String getFieldName() {
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
        public List<String> getValidationMessages() {
            return validationMessages;
        }

        @Override
        @Nullable
        public PropertyAnnotationHandler getPropertyValueVisitor() {
            return annotationHandler;
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
        public Method getMethod() {
            return method;
        }
    }

}
