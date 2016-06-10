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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.GroovyObject;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.execution.TaskValidator;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class TaskClassValidator implements TaskValidator, Action<Task> {
    // Avoid reflecting on classes we know we don't need to look at
    private static final Collection<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.<Class<?>>of(
        ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class
    );

    private final static ValidationAction NOT_NULL_VALIDATOR = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
            if (value == null) {
                messages.add(String.format("No value has been specified for property '%s'.", propertyName));
            }
        }
    };

    @SuppressWarnings("deprecation")
    private final static List<? extends PropertyAnnotationHandler> HANDLERS = Arrays.asList(
        new InputFilePropertyAnnotationHandler(),
        new InputDirectoryPropertyAnnotationHandler(),
        new InputFilesPropertyAnnotationHandler(),
        new OutputFilePropertyAnnotationHandler(),
        new OutputFilesPropertyAnnotationHandler(),
        new OutputDirectoryPropertyAnnotationHandler(),
        new OutputDirectoriesPropertyAnnotationHandler(),
        new InputPropertyAnnotationHandler(),
        new NestedBeanPropertyAnnotationHandler(),
        new NoOpPropertyAnnotationHandler(Inject.class),
        new NoOpPropertyAnnotationHandler(Console.class),
        new NoOpPropertyAnnotationHandler(Internal.class),
        new NoOpPropertyAnnotationHandler(OptionValues.class)
    );

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

    public <T> void attachActions(final TaskPropertyInfo parent, Class<T> type) {
        final Map<String, TaskPropertyInfo> properties = Maps.newHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                Map<String, Field> fields = getFields(type);
                for (Method method : type.getDeclaredMethods()) {
                    PropertyAccessorType accessorType = PropertyAccessorType.of(method);
                    if (accessorType == null || accessorType == PropertyAccessorType.SETTER || method.isBridge() || GroovyMethods.isObjectMethod(method)) {
                        continue;
                    }

                    String fieldName = accessorType.propertyNameFor(method);
                    Field field = fields.get(fieldName);
                    String propertyName = parent != null ? parent.getName() + '.' + fieldName : fieldName;

                    TaskPropertyInfo propertyInfo = properties.get(propertyName);
                    if (propertyInfo == null) {
                        propertyInfo = new TaskPropertyInfo(TaskClassValidator.this, parent, propertyName, method, field);
                        properties.put(propertyName, propertyInfo);
                    } else {
                        if (propertyInfo.getInstanceVariableField() == null && field != null) {
                            propertyInfo.setInstanceVariableField(field);
                        }
                    }
                    propertyInfo.addAnnotations(method.getDeclaredAnnotations());
                    if (field != null) {
                        propertyInfo.addAnnotations(field.getDeclaredAnnotations());
                    }
                }
            }
        });
        for (TaskPropertyInfo propertyInfo : properties.values()) {
            boolean annotationFound = attachValidationActions(propertyInfo);

            if (propertyInfo.isValidationRequired()) {
                validatedProperties.add(propertyInfo);
            }

            String propertyName = propertyInfo.getName();
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

    private boolean attachValidationActions(TaskPropertyInfo propertyInfo) {
        boolean annotationFound = false;
        for (PropertyAnnotationHandler handler : HANDLERS) {
            annotationFound |= attachValidationAction(handler, propertyInfo);
        }
        return annotationFound;
    }

    private boolean attachValidationAction(PropertyAnnotationHandler handler, TaskPropertyInfo propertyInfo) {
        Class<? extends Annotation> annotationType = handler.getAnnotationType();

        if (!propertyInfo.isAnnotationPresent(annotationType)) {
            return false;
        }

        if (handler.getMustNotBeNullByDefault() && !propertyInfo.isAnnotationPresent(Optional.class)) {
            propertyInfo.setNotNullValidator(NOT_NULL_VALIDATOR);
        }

        propertyInfo.attachActions(handler);
        return true;
    }
}
