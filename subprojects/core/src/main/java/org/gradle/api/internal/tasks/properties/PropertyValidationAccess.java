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
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.properties.annotations.ClasspathPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.CompileClasspathPropertyAnnotationHandler;
import org.gradle.api.tasks.Nested;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Class for easy access to task property validation from the validator task.
 */
@NonNullApi
public class PropertyValidationAccess {
    @SuppressWarnings("unused")
    public static void collectTaskValidationProblems(Class<?> beanClass, Map<String, Boolean> problems) {
        PropertyMetadataStore metadataStore = new DefaultPropertyMetadataStore(ImmutableList.of(
            new ClasspathPropertyAnnotationHandler(), new CompileClasspathPropertyAnnotationHandler()
        ));
        Queue<ClassNode> queue = new ArrayDeque<ClassNode>();
        queue.add(new ClassNode(null, beanClass));

        while (!queue.isEmpty()) {
            ClassNode node = queue.remove();
            Set<PropertyMetadata> typeMetadata = metadataStore.getTypeMetadata(node.getBeanClass());
            for (PropertyMetadata metadata : typeMetadata) {
                String qualifiedPropertyName = node.getQualifiedPropertyName(metadata.getFieldName());
                for (String validationMessage : metadata.getValidationMessages()) {
                    problems.put(propertyValidationMessage(beanClass, qualifiedPropertyName, validationMessage), Boolean.FALSE);
                }
                if (metadata.getPropertyValueVisitor() == null) {
                    if (!Modifier.isPrivate(metadata.getMethod().getModifiers())) {
                        problems.put(propertyValidationMessage(beanClass, qualifiedPropertyName, "is not annotated with an input or output annotation"), Boolean.FALSE);
                    }
                    continue;
                }
                if (metadata.isAnnotationPresent(Nested.class)) {
                    queue.add(new ClassNode(qualifiedPropertyName, metadata.getMethod().getReturnType()));
                }
            }
        }
    }

    private static String propertyValidationMessage(Class<?> task, String qualifiedPropertyName, String validationMessage) {
        return String.format("Task type '%s': property '%s' %s.", task.getName(), qualifiedPropertyName, validationMessage);
    }

    private static class ClassNode {
        private final String parentPropertyName;
        private final Class<?> propertyClass;

        public ClassNode(@Nullable String parentPropertyName, Class<?> beanClass) {
            this.parentPropertyName = parentPropertyName;
            this.propertyClass = beanClass;
        }

        public Class<?> getBeanClass() {
            return propertyClass;
        }

        public String getQualifiedPropertyName(String propertyName) {
            return parentPropertyName == null ? propertyName : parentPropertyName + "." + propertyName;
        }
    }
}
