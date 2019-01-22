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
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.reflect.PropertyMetadata;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
    public static void collectTaskValidationProblems(Class<?> topLevelBean, Map<String, Boolean> problems, boolean enableStricterValidation) {
        DefaultCrossBuildInMemoryCacheFactory cacheFactory = new DefaultCrossBuildInMemoryCacheFactory(new DefaultListenerManager());
        DefaultTaskClassInfoStore taskClassInfoStore = new DefaultTaskClassInfoStore(cacheFactory);
        TypeMetadataStore metadataStore = new DefaultTypeMetadataStore(ImmutableList.of(
            new ClasspathPropertyAnnotationHandler(), new CompileClasspathPropertyAnnotationHandler()
        ), cacheFactory);
        Queue<BeanTypeNode<?>> queue = new ArrayDeque<BeanTypeNode<?>>();
        BeanTypeNodeFactory nodeFactory = new BeanTypeNodeFactory(metadataStore);
        queue.add(nodeFactory.createRootNode(TypeToken.of(topLevelBean)));
        boolean cacheable = taskClassInfoStore.getTaskClassInfo(Cast.<Class<? extends Task>>uncheckedCast(topLevelBean)).isCacheable();
        boolean stricterValidation = enableStricterValidation || cacheable;

        while (!queue.isEmpty()) {
            BeanTypeNode<?> node = queue.remove();
            node.visit(topLevelBean, stricterValidation, problems, queue, nodeFactory);
        }
    }

    private static class BeanTypeNodeFactory {
        private final TypeMetadataStore metadataStore;

        public BeanTypeNodeFactory(TypeMetadataStore metadataStore) {
            this.metadataStore = metadataStore;
        }

        public BeanTypeNode<?> createRootNode(TypeToken<?> beanType) {
            return new NestedBeanTypeNode(null, null, beanType, metadataStore.getTypeMetadata(beanType.getRawType()));
        }

        public void createAndAddToQueue(BeanTypeNode<?> parentNode, String propertyName, TypeToken<?> beanType, Queue<BeanTypeNode<?>> queue) {
            if (!parentNode.nodeCreatesCycle(beanType)) {
                queue.add(createChild(parentNode, propertyName, beanType));
            }
        }

        private BeanTypeNode<?> createChild(BeanTypeNode<?> parentNode, String propertyName, TypeToken<?> beanType) {
            Class<?> rawType = beanType.getRawType();
            TypeMetadata typeMetadata = metadataStore.getTypeMetadata(rawType);
            if (!typeMetadata.hasAnnotatedProperties()) {
                if (Map.class.isAssignableFrom(rawType)) {
                    return new MapBeanTypeNode(parentNode, propertyName, Cast.<TypeToken<Map<?, ?>>>uncheckedCast(beanType), typeMetadata);
                }
                if (Iterable.class.isAssignableFrom(rawType)) {
                    return new IterableBeanTypeNode(parentNode, propertyName, Cast.<TypeToken<Iterable<?>>>uncheckedCast(beanType), typeMetadata);
                }
            }
            return new NestedBeanTypeNode(parentNode, propertyName, beanType, typeMetadata);
        }
    }

    private abstract static class BeanTypeNode<T> extends AbstractPropertyNode<TypeToken<?>> {

        protected BeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String propertyName, TypeToken<? extends T> beanType, TypeMetadata typeMetadata) {
            super(parentNode, propertyName, typeMetadata);
            this.beanType = beanType;
        }

        public abstract void visit(Class<?> topLevelBean, boolean stricterValidation, Map<String, Boolean> problems, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory);

        public boolean nodeCreatesCycle(TypeToken<?> childType) {
            return findNodeCreatingCycle(childType, Equivalence.equals()) != null;
        }
        private final TypeToken<? extends T> beanType;

        protected TypeToken<?> extractNestedType(Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
            return PropertyValidationAccess.extractNestedType(beanType, parameterizedSuperClass, typeParameterIndex);
        }

        @Override
        protected TypeToken<?> getNodeValue() {
            return beanType;
        }
    }

    private static class NestedBeanTypeNode extends BeanTypeNode<Object> {

        public NestedBeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String parentPropertyName, TypeToken<?> beanType, TypeMetadata typeMetadata) {
            super(parentNode, parentPropertyName, beanType, typeMetadata);
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean stricterValidation, Map<String, Boolean> problems, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory) {
            for (PropertyMetadata propertyMetadata : getTypeMetadata().getPropertiesMetadata()) {
                String qualifiedPropertyName = getQualifiedPropertyName(propertyMetadata.getPropertyName());
                for (String validationMessage : propertyMetadata.getValidationMessages()) {
                    problems.put(propertyValidationMessage(topLevelBean, qualifiedPropertyName, validationMessage), Boolean.FALSE);
                }
                Class<? extends Annotation> propertyType = propertyMetadata.getPropertyType();
                if (propertyType == null) {
                    if (!Modifier.isPrivate(propertyMetadata.getGetterMethod().getModifiers())) {
                        problems.put(propertyValidationMessage(topLevelBean, qualifiedPropertyName, "is not annotated with an input or output annotation"), Boolean.FALSE);
                    }
                    continue;
                }
                PropertyValidator validator = PROPERTY_VALIDATORS.get(propertyType);
                if (validator != null) {
                    String validationMessage = validator.validate(stricterValidation, propertyMetadata);
                    if (validationMessage != null) {
                        problems.put(propertyValidationMessage(topLevelBean, qualifiedPropertyName, validationMessage), Boolean.FALSE);
                    }
                }
                if (propertyMetadata.isAnnotationPresent(Nested.class)) {
                    TypeToken<?> beanType = unpackProvider(propertyMetadata.getGetterMethod());
                    nodeFactory.createAndAddToQueue(this, qualifiedPropertyName, beanType, queue);
                }
            }
        }

        private static TypeToken<?> unpackProvider(Method method) {
            Class<?> rawType = method.getReturnType();
            TypeToken<?> genericReturnType = TypeToken.of(method.getGenericReturnType());
            if (Provider.class.isAssignableFrom(rawType)) {
                    return PropertyValidationAccess.extractNestedType(Cast.<TypeToken<Provider<?>>>uncheckedCast(genericReturnType), Provider.class, 0);
            }
            return genericReturnType;
        }

        private static String propertyValidationMessage(Class<?> task, String qualifiedPropertyName, String validationMessage) {
            return String.format("Task type '%s': property '%s' %s.", task.getName(), qualifiedPropertyName, validationMessage);
        }
    }

    private static class IterableBeanTypeNode extends BeanTypeNode<Iterable<?>> {

        public IterableBeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String parentPropertyName, TypeToken<Iterable<?>> iterableType, TypeMetadata typeMetadata) {
            super(parentNode, parentPropertyName, iterableType, typeMetadata);
        }

        private String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                ? getQualifiedPropertyName("<name>")
                : getPropertyName() + "*";
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean stricterValidation, Map<String, Boolean> problems, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory) {
            TypeToken<?> nestedType = extractNestedType(Iterable.class, 0);
            nodeFactory.createAndAddToQueue(this, determinePropertyName(nestedType), nestedType, queue);
        }
    }

    private static class MapBeanTypeNode extends BeanTypeNode<Map<?, ?>> {

        public MapBeanTypeNode(@Nullable BeanTypeNode<?> parentNode, @Nullable String parentPropertyName, TypeToken<Map<?, ?>> mapType, TypeMetadata typeMetadata) {
            super(parentNode, parentPropertyName, mapType, typeMetadata);
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean stricterValidation, Map<String, Boolean> problems, Queue<BeanTypeNode<?>> queue, BeanTypeNodeFactory nodeFactory) {
            TypeToken<?> nestedType = extractNestedType(Map.class, 1);
            nodeFactory.createAndAddToQueue(this, getQualifiedPropertyName("<key>"), nestedType, queue);
        }
    }

    private interface PropertyValidator {
        @Nullable
        String validate(boolean stricterValidation, PropertyMetadata metadata);
    }

    private static class InputOnFileTypeValidator implements PropertyValidator {
        @Nullable
        @Override
        public String validate(boolean stricterValidation, PropertyMetadata metadata) {
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
        public String validate(boolean stricterValidation, PropertyMetadata metadata) {
            PathSensitive pathSensitive = metadata.getAnnotation(PathSensitive.class);
            if (stricterValidation && pathSensitive == null) {
                return "is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE";
            }
            return null;
        }
    }

    private static <T> TypeToken<?> extractNestedType(TypeToken<T> beanType, Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
        ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedSuperClass).getType();
        return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
    }
}
