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

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
        Queue<BeanTypeNodeContext> queue = new ArrayDeque<BeanTypeNodeContext>();
        queue.add(new BeanTypeNodeContext(BeanTypeNode.create(null, TypeToken.of(topLevelBean), metadataStore, null), queue));
        boolean cacheable = taskClassInfoStore.getTaskClassInfo(Cast.<Class<? extends Task>>uncheckedCast(topLevelBean)).isCacheable();

        while (!queue.isEmpty()) {
            BeanTypeNodeContext context = queue.remove();
            if (!context.currentNodeCreatesCycle()) {
                context.getCurrentNode().visit(topLevelBean, cacheable, problems, context, metadataStore);
            }
        }
    }

    private static class BeanTypeNodeContext extends AbstractNodeContext<BeanTypeNode> {

        private static final Equivalence<BeanTypeNode> EQUAL_TYPES = Equivalence.equals().onResultOf(new Function<BeanTypeNode, TypeToken<?>>() {
            @Override
            public TypeToken<?> apply(BeanTypeNode input) {
                return input.getBeanType();
            }
        });

        private final Queue<BeanTypeNodeContext> queue;

        public BeanTypeNodeContext(BeanTypeNode currentNode, Queue<BeanTypeNodeContext> queue) {
            super(currentNode);
            this.queue = queue;
        }

        public BeanTypeNodeContext(BeanTypeNode currentNode, AbstractNodeContext<BeanTypeNode> parent, Queue<BeanTypeNodeContext> queue) {
            super(currentNode, parent);
            this.queue = queue;
        }

        @Override
        public void addSubProperties(BeanTypeNode node) {
            queue.add(new BeanTypeNodeContext(node, this, queue));
        }

        @Override
        protected Equivalence<BeanTypeNode> getNodeEquivalence() {
            return EQUAL_TYPES;
        }
    }

    private abstract static class BeanTypeNode extends AbstractPropertyNode {

        public static BeanTypeNode create(@Nullable String parentPropertyName, TypeToken<?> beanType, PropertyMetadataStore metadataStore, BeanTypeNode parentNode) {
            Class<?> rawType = beanType.getRawType();
            TypeMetadata typeMetadata = metadataStore.getTypeMetadata(rawType);
            if (parentPropertyName != null && !typeMetadata.hasAnnotatedProperties()) {
                if (Map.class.isAssignableFrom(rawType)) {
                    return new MapBeanTypeNode(parentPropertyName, Cast.<TypeToken<Map<?, ?>>>uncheckedCast(beanType), parentNode);
                }
                if (Iterable.class.isAssignableFrom(rawType)) {
                    return new IterableBeanTypeNode(parentPropertyName, Cast.<TypeToken<Iterable<?>>>uncheckedCast(beanType), parentNode);
                }
            }
            return new NestedBeanTypeNode(parentPropertyName, beanType, parentNode);
        }

        protected BeanTypeNode(@Nullable String propertyName, Class<?> beanClass, BeanTypeNode parentNode) {
            super(propertyName, beanClass, parentNode);
        }

        public abstract void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, BeanTypeNodeContext context, PropertyMetadataStore metadataStore);

        public abstract TypeToken<?> getBeanType();
    }

    private static abstract class BaseBeanTypeNode<T> extends BeanTypeNode {
        private final TypeToken<? extends T> beanType;

        protected BaseBeanTypeNode(@Nullable String parentPropertyName, TypeToken<? extends T> beanType, BeanTypeNode parentNode) {
            super(parentPropertyName, beanType.getRawType(), parentNode);
            this.beanType = beanType;
        }

        protected TypeToken<?> extractNestedType(Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
            ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedSuperClass).getType();
            return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
        }

        @Override
        public TypeToken<? extends T> getBeanType() {
            return beanType;
        }
    }

    private static class NestedBeanTypeNode extends BaseBeanTypeNode<Object> {

        public NestedBeanTypeNode(@Nullable String parentPropertyName, TypeToken<?> beanType, BeanTypeNode parentNode) {
            super(parentPropertyName, beanType, parentNode);
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, BeanTypeNodeContext context, PropertyMetadataStore metadataStore) {
            validateBeanProperties(topLevelBean, cacheable, problems, this, context, metadataStore);
        }

        private static void validateBeanProperties(Class<?> beanClass, boolean cacheable, Map<String, Boolean> problems, BeanTypeNode node, BeanTypeNodeContext context, PropertyMetadataStore metadataStore) {
            for (PropertyMetadata metadata : metadataStore.getTypeMetadata(node.getBeanClass()).getPropertiesMetadata()) {
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
                    context.addSubProperties(BeanTypeNode.create(qualifiedPropertyName, TypeToken.of(metadata.getMethod().getGenericReturnType()), metadataStore, node));
                }
            }
        }

        private static String propertyValidationMessage(Class<?> task, String qualifiedPropertyName, String validationMessage) {
            return String.format("Task type '%s': property '%s' %s.", task.getName(), qualifiedPropertyName, validationMessage);
        }
    }

    private static class IterableBeanTypeNode extends BaseBeanTypeNode<Iterable<?>> {

        public IterableBeanTypeNode(@Nullable String parentPropertyName, TypeToken<Iterable<?>> iterableType, BeanTypeNode parentNode) {
            super(parentPropertyName, iterableType, parentNode);
        }

        private String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                ? getQualifiedPropertyName("<name>")
                : getPropertyName() + "*";
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, BeanTypeNodeContext context, PropertyMetadataStore metadataStore) {
            TypeToken<?> nestedType = extractNestedType(Iterable.class, 0);
            context.addSubProperties(BeanTypeNode.create(determinePropertyName(nestedType), nestedType, metadataStore, this));
        }
    }

    private static class MapBeanTypeNode extends BaseBeanTypeNode<Map<?, ?>> {

        public MapBeanTypeNode(@Nullable String parentPropertyName, TypeToken<Map<?, ?>> mapType, BeanTypeNode parentNode) {
            super(parentPropertyName, mapType, parentNode);
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, BeanTypeNodeContext context, PropertyMetadataStore metadataStore) {
            TypeToken<?> nestedType = extractNestedType(Map.class, 1);
            context.addSubProperties(BeanTypeNode.create(getQualifiedPropertyName("<key>"), nestedType, metadataStore, this));
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
}
