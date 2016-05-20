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

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
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
    private ValidationAction validationAction = NO_OP_VALIDATION_ACTION;
    private ValidationAction notNullValidator = NO_OP_VALIDATION_ACTION;
    private UpdateAction configureAction = NO_OP_CONFIGURATION_ACTION;
    private boolean validationRequired;
    private final Field instanceVariableField;

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

    @Override
    public Class<?> getType() {
        return method.getReturnType();
    }

    @Override
    public Class<?> getInstanceVariableType() {
        return instanceVariableField != null ? instanceVariableField.getType() : null;
    }

    @Override
    public Method getTarget() {
        return method;
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
        handler.attachActions(this);
        validationRequired = true;
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
