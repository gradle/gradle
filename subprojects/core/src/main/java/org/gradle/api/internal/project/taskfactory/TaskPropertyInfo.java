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

import com.google.common.collect.Lists;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class TaskPropertyInfo implements TaskPropertyActionContext {
    private static final ValidationAction NO_OP_VALIDATION_ACTION = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
        }
    };
    private static final TaskPropertyValue NO_OP_VALUE = new TaskPropertyValue() {
        public Object getValue() {
            return null;
        }

        public void checkNotNull(Collection<String> messages) {
        }

        public void checkValid(Collection<String> messages) {
        }
    };
    private static final UpdateAction NO_OP_CONFIGURATION_ACTION = new UpdateAction() {
        public void update(TaskInternal task, Callable<Object> futureValue) {
        }
    };

    private final TaskClassValidator validator;
    private final TaskPropertyInfo parent;
    private final String propertyName;
    private final Method method;
    // Would be more correct as a Set, but lists are cheaper and duplicates don't actually cause any trouble here
    private final List<Annotation> annotations = Lists.newLinkedList();
    private ValidationAction validationAction = NO_OP_VALIDATION_ACTION;
    private ValidationAction notNullValidator = NO_OP_VALIDATION_ACTION;
    private UpdateAction configureAction = NO_OP_CONFIGURATION_ACTION;
    private boolean validationRequired;
    private Field instanceVariableField;

    TaskPropertyInfo(TaskClassValidator validator, TaskPropertyInfo parent, String propertyName, Method method, Field instanceVariableField) {
        this.validator = validator;
        this.parent = parent;
        this.propertyName = propertyName;
        this.method = method;
        this.instanceVariableField = instanceVariableField;
    }

    @Override
    public String toString() {
        return propertyName;
    }

    @Override
    public String getName() {
        return propertyName;
    }

    public Field getInstanceVariableField() {
        return instanceVariableField;
    }

    public void setInstanceVariableField(Field instanceVariableField) {
        this.instanceVariableField = instanceVariableField;
    }

    @Override
    public Class<?> getType() {
        return instanceVariableField != null ? instanceVariableField.getType() : method.getReturnType();
    }

    public void addAnnotations(Annotation... annotations) {
        Collections.addAll(this.annotations, annotations);
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return Cast.uncheckedCast(annotation);
            }
        }
        return null;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return getAnnotation(annotationType) != null;
    }

    @Override
    public void setValidationAction(ValidationAction action) {
        validationAction = action;
    }

    public UpdateAction getConfigureAction() {
        return configureAction;
    }

    @Override
    public void setConfigureAction(UpdateAction action) {
        configureAction = action;
    }

    public void setNotNullValidator(ValidationAction notNullValidator) {
        this.notNullValidator = notNullValidator;
    }

    @Override
    public void attachActions(Class<?> type) {
        validator.attachActions(this, type);
    }

    public void attachActions(PropertyAnnotationHandler handler) {
        if (handler.attachActions(this)) {
            validationRequired = true;
        }
    }

    public boolean isValidationRequired() {
        return validationRequired;
    }

    public TaskPropertyValue getValue(Object rootObject) {
        Object bean = rootObject;
        if (parent != null) {
            TaskPropertyValue parentValue = parent.getValue(rootObject);
            if (parentValue.getValue() == null) {
                return NO_OP_VALUE;
            }
            bean = parentValue.getValue();
        }

        final Object finalBean = bean;
        final Object value = DeprecationLogger.whileDisabled(new Factory<Object>() {
            public Object create() {
                return JavaReflectionUtil.method(finalBean, Object.class, method).invoke(finalBean);
            }
        });

        return new TaskPropertyValue() {
            @Override
            public Object getValue() {
                return value;
            }

            @Override
            public void checkNotNull(Collection<String> messages) {
                notNullValidator.validate(propertyName, value, messages);
            }

            @Override
            public void checkValid(Collection<String> messages) {
                if (value != null) {
                    validationAction.validate(propertyName, value, messages);
                }
            }
        };
    }
}
