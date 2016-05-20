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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.execution.TaskValidator;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class TaskClassValidator implements TaskValidator, Action<Task> {
    private final static Transformer<Iterable<File>, Object> FILE_PROPERTY_TRANSFORMER = new Transformer<Iterable<File>, Object>() {
        public Iterable<File> transform(Object original) {
            File file = (File) original;
            return file == null ? Collections.<File>emptyList() : Collections.singleton(file);
        }
    };

    private final static Transformer<Iterable<File>, Object> ITERABLE_FILE_PROPERTY_TRANSFORMER = new Transformer<Iterable<File>, Object>() {
        @SuppressWarnings("unchecked")
        public Iterable<File> transform(Object original) {
            return original != null ? (Iterable<File>) original : Collections.<File>emptyList();
        }
    };

    private final static ValidationAction NOT_NULL_VALIDATOR = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
            if (value == null) {
                messages.add(String.format("No value has been specified for property '%s'.", propertyName));
            }
        }
    };

    private final static List<? extends PropertyAnnotationHandler> HANDLERS = Arrays.asList(
        new InputFilePropertyAnnotationHandler(),
        new InputDirectoryPropertyAnnotationHandler(),
        new InputFilesPropertyAnnotationHandler(),
        new OutputFilePropertyAnnotationHandler(OutputFile.class, FILE_PROPERTY_TRANSFORMER),
        new OutputFilePropertyAnnotationHandler(OutputFiles.class, ITERABLE_FILE_PROPERTY_TRANSFORMER),
        new OutputDirectoryPropertyAnnotationHandler(OutputDirectory.class, FILE_PROPERTY_TRANSFORMER),
        new OutputDirectoryPropertyAnnotationHandler(OutputDirectories.class, ITERABLE_FILE_PROPERTY_TRANSFORMER),
        new InputPropertyAnnotationHandler(),
        new NestedBeanPropertyAnnotationHandler(),
        new InjectedPropertyAnnotationHandler());

    private final Set<TaskPropertyInfo> validatedProperties = Sets.newLinkedHashSet();
    private final Set<String> allPropertyNames = Sets.newTreeSet();
    private final Set<String> annotatedPropertyNames = Sets.newTreeSet();

    @Override
    public void execute(Task task) {
    }

    public void addInputsAndOutputs(final TaskInternal task) {
        task.addValidator(this);
        for (final TaskPropertyInfo property : validatedProperties) {
            Callable<Object> futureValue = new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return property.getValue(task).getValue();
                }
            };

            property.getConfigureAction().update(task, futureValue);
        }
    }

    @Override
    public void validate(TaskInternal task, Collection<String> messages) {
        List<TaskPropertyValue> propertyValues = new ArrayList<TaskPropertyValue>();
        for (TaskPropertyInfo property : validatedProperties) {
            propertyValues.add(property.getValue(task));
        }
        for (TaskPropertyValue propertyValue : propertyValues) {
            propertyValue.checkNotNull(messages);
        }
        for (TaskPropertyValue propertyValue : propertyValues) {
            propertyValue.checkValid(messages);
        }
    }

    public void attachActions(TaskPropertyInfo parent, Class<?> type) {
        Class<?> superclass = type.getSuperclass();
        if (!(superclass == null
                // Avoid reflecting on classes we know we don't need to look at
                || superclass.equals(ConventionTask.class) || superclass.equals(DefaultTask.class)
                || superclass.equals(AbstractTask.class) || superclass.equals(Object.class)
        )) {
            attachActions(parent, superclass);
        }

        Map<String, Field> fields = getFields(type);
        for (Method method : type.getDeclaredMethods()) {
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            if (accessorType == null || accessorType == PropertyAccessorType.SETTER || method.isBridge() || GroovyMethods.isObjectMethod(method)) {
                continue;
            }

            String fieldName = accessorType.propertyNameFor(method);
            Field field = fields.get(fieldName);
            String propertyName = parent != null ? parent.getName() + '.' + fieldName : fieldName;

            TaskPropertyInfo propertyInfo = new TaskPropertyInfo(this, parent, propertyName, method, field);

            boolean annotationFound = attachValidationActions(propertyInfo, field);

            if (propertyInfo.isValidationRequired()) {
                validatedProperties.add(propertyInfo);
            }

            allPropertyNames.add(propertyName);
            if (annotationFound) {
                annotatedPropertyNames.add(propertyName);
            }
        }
    }

    public boolean hasAnythingToValidate() {
        return !validatedProperties.isEmpty();
    }

    public Set<TaskPropertyInfo> getValidatedProperties() {
        return validatedProperties;
    }

    public Set<String> getNonAnnotatedPropertyNames() {
        return Sets.difference(allPropertyNames, annotatedPropertyNames);
    }

    private Map<String, Field> getFields(Class<?> type) {
        Map<String, Field> fields = Maps.newHashMap();
        for (Field field : type.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
        return fields;
    }

    private boolean attachValidationActions(TaskPropertyInfo propertyInfo, Field field) {
        final Method method = propertyInfo.getTarget();
        boolean annotationFound = false;
        for (PropertyAnnotationHandler handler : HANDLERS) {
            annotationFound |= attachValidationAction(handler, propertyInfo, method, field);
        }
        return annotationFound;
    }

    private boolean attachValidationAction(PropertyAnnotationHandler handler, TaskPropertyInfo propertyInfo, Method method, Field field) {
        Class<? extends Annotation> annotationType = handler.getAnnotationType();

        AnnotatedElement annotationTarget = null;
        if (method.getAnnotation(annotationType) != null) {
            annotationTarget = method;
        } else if (field != null && field.getAnnotation(annotationType) != null) {
            annotationTarget = field;
        }
        if (annotationTarget == null) {
            return false;
        }

        Annotation optional = annotationTarget.getAnnotation(org.gradle.api.tasks.Optional.class);
        if (optional == null) {
            propertyInfo.setNotNullValidator(NOT_NULL_VALIDATOR);
        }

        propertyInfo.attachActions(handler);
        return true;
    }
}
