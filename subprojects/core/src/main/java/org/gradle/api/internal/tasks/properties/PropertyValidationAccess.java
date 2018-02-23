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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Named;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.taskfactory.DefaultTaskClassInfoStore;
import org.gradle.api.internal.tasks.properties.annotations.ClasspathPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.CompileClasspathPropertyAnnotationHandler;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

/**
 * Class for easy access to task property validation from the validator task.
 */
@NonNullApi
public class PropertyValidationAccess {
    private final static Map<Class<? extends Annotation>, PropertyValidator> PROPERTY_VALIDATORS = ImmutableMap.of(
        Input.class, new InputOnFileTypeValidator(),
        InputFiles.class, new MissingPathSensitivityValidator(),
        InputFile.class, new MissingPathSensitivityValidator(),
        InputDirectory.class, new MissingPathSensitivityValidator()
    );

    @SuppressWarnings("unused")
    public static void collectTaskValidationProblems(Class<?> topLevelBean, Map<String, Boolean> problems) {
        DefaultTaskClassInfoStore taskClassInfoStore = new DefaultTaskClassInfoStore();
        PropertyMetadataStore metadataStore = new DefaultPropertyMetadataStore(ImmutableList.of(
            new ClasspathPropertyAnnotationHandler(), new CompileClasspathPropertyAnnotationHandler()
        ));
        Queue<BeanTypeNode> queue = new ArrayDeque<BeanTypeNode>();
        queue.add(new BeanTypeNode(null, TypeToken.of(topLevelBean)));
        boolean cacheable = taskClassInfoStore.getTaskClassInfo(Cast.<Class<? extends Task>>uncheckedCast(topLevelBean)).isCacheable();

        while (!queue.isEmpty()) {
            BeanTypeNode node = queue.remove();
            validateTaskClass(topLevelBean, cacheable, problems, node, new NestedTypeContext(metadataStore, queue), metadataStore.getTypeMetadata(node.getBeanClass()));
        }
    }

    private static void validateTaskClass(Class<?> beanClass, boolean cacheable, Map<String, Boolean> problems, BeanTypeNode node, NestedPropertyContext<BeanTypeNode> context, TypeMetadata typeMetadata) {
        for (PropertyMetadata metadata : typeMetadata.getPropertiesMetadata()) {
            String qualifiedPropertyName = node.getQualifiedPropertyName(metadata.getFieldName());
            for (String validationMessage : metadata.getValidationMessages()) {
                problems.put(propertyValidationMessage(beanClass, qualifiedPropertyName, validationMessage), Boolean.FALSE);
            }
            Class<? extends Annotation> propertyType = metadata.getPropertyType();
            if (propertyType == null) {
                if (!Modifier.isPrivate(metadata.getMethod().getModifiers())) {
                    problems.put(propertyValidationMessage(beanClass, qualifiedPropertyName, "is not annotated with an input or output annotation"), Boolean.FALSE);
                }
                continue;
            } else if (PROPERTY_VALIDATORS.containsKey(propertyType)) {
                PropertyValidator validator = PROPERTY_VALIDATORS.get(propertyType);
                String validationMessage = validator.validate(cacheable, metadata);
                if (validationMessage != null) {
                    problems.put(propertyValidationMessage(beanClass, qualifiedPropertyName, validationMessage), Boolean.FALSE);
                }
            }
            if (metadata.isAnnotationPresent(Nested.class)) {
                AbstractNestedPropertyContext.collectNestedProperties(
                    new BeanTypeNode(qualifiedPropertyName, TypeToken.of(metadata.getMethod().getGenericReturnType())),
                    context
                );
            }
        }
    }

    private static String propertyValidationMessage(Class<?> task, String qualifiedPropertyName, String validationMessage) {
        return String.format("Task type '%s': property '%s' %s.", task.getName(), qualifiedPropertyName, validationMessage);
    }

    private static class BeanTypeNode extends AbstractPropertyNode<BeanTypeNode> {
        private final TypeToken<?> beanType;

        public BeanTypeNode(@Nullable String parentPropertyName, TypeToken<?> beanType) {
            super(parentPropertyName, beanType.getRawType());
            this.beanType = beanType;
        }

        @Override
        public Iterator<BeanTypeNode> getIterator() {
            if (Map.class.isAssignableFrom(beanType.getRawType())) {
                TypeToken<?> nestedType = extractNestedType(Map.class, 1);
                return Iterators.singletonIterator(new BeanTypeNode(getQualifiedPropertyName("<key>"), nestedType));
            }
            TypeToken<?> nestedType = extractNestedType(Iterable.class, 0);
            return Iterators.singletonIterator(new BeanTypeNode(determinePropertyName(nestedType), nestedType));
        }

        private String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                        ? getQualifiedPropertyName("<name>")
                        : getPropertyName() + "*";
        }

        private <T> TypeToken<?> extractNestedType(Class<T> parameterizedSuperClass, int typeParameterIndex) {
            TypeToken<T> typeToken = Cast.uncheckedCast(beanType);
            ParameterizedType type = (ParameterizedType) typeToken.getSupertype(parameterizedSuperClass).getType();
            return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
        }
    }

    private interface PropertyValidator {
        @Nullable
        String validate(boolean cacheable, PropertyMetadata metadata);
    }

    private static class InputOnFileTypeValidator implements PropertyValidator {
        @SuppressWarnings("Since15")
        @Nullable
        @Override
        public String validate(boolean cacheable, PropertyMetadata metadata) {
            Class<?> valueType = metadata.getDeclaredType();
            if (File.class.isAssignableFrom(valueType)
                || java.nio.file.Path.class.isAssignableFrom(valueType)
                || FileCollection.class.isAssignableFrom(valueType)) {
                return "has @Input annotation used on property of type " + valueType.getName();
            }
            return null;
        }
    }

    private static class MissingPathSensitivityValidator implements PropertyValidator {

        @Nullable
        @Override
        public String validate(boolean cacheable, PropertyMetadata metadata) {
            PathSensitive pathSensitive = metadata.getAnnotation(PathSensitive.class);
            if (cacheable && pathSensitive == null) {
                return "is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE";
            }
            return null;
        }
    }

    private static class NestedTypeContext extends AbstractNestedPropertyContext<BeanTypeNode> {

        private final Queue<BeanTypeNode> queue;

        public NestedTypeContext(PropertyMetadataStore metadataStore, Queue<BeanTypeNode> queue) {
            super(metadataStore);
            this.queue = queue;
        }

        @Override
        public void addNested(BeanTypeNode node) {
            queue.add(node);
        }
    }

}
