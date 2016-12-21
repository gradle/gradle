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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.Callable;

public class TaskPropertyInfo implements Comparable<TaskPropertyInfo> {
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

    private final TaskPropertyInfo parent;
    private final String propertyName;
    private final Class<? extends Annotation> propertyType;
    private final Method method;
    private final ValidationAction validationAction;
    private final UpdateAction configureAction;
    private final boolean optional;

    TaskPropertyInfo(TaskPropertyInfo parent, String propertyName, Class<? extends Annotation> propertyType, Method method, ValidationAction validationAction, UpdateAction configureAction, boolean optional) {
        this.parent = parent;
        this.propertyName = propertyName;
        this.propertyType = propertyType;
        this.method = method;
        this.validationAction = validationAction == null ? NO_OP_VALIDATION_ACTION : validationAction;
        this.configureAction = configureAction == null ? NO_OP_CONFIGURATION_ACTION : configureAction;
        this.optional = optional;
    }

    @Override
    public String toString() {
        return String.format("@%s %s", propertyType.getSimpleName(), propertyName);
    }

    public String getName() {
        return propertyName;
    }

    public Class<? extends Annotation> getPropertyType() {
        return propertyType;
    }

    public UpdateAction getConfigureAction() {
        return configureAction;
    }

    public TaskPropertyValue getValue(Object rootObject) {
        final Object bean;
        if (parent != null) {
            TaskPropertyValue parentValue = parent.getValue(rootObject);
            if (parentValue.getValue() == null) {
                return NO_OP_VALUE;
            }
            bean = parentValue.getValue();
        } else {
            bean = rootObject;
        }

        final Object value = DeprecationLogger.whileDisabled(new Factory<Object>() {
            public Object create() {
                return JavaReflectionUtil.method(Object.class, method).invoke(bean);
            }
        });

        return new TaskPropertyValue() {
            @Override
            public Object getValue() {
                return value;
            }

            @Override
            public void checkNotNull(Collection<String> messages) {
                if (value == null && !optional) {
                    messages.add(String.format("No value has been specified for property '%s'.", propertyName));
                }
            }

            @Override
            public void checkValid(Collection<String> messages) {
                if (value != null) {
                    validationAction.validate(propertyName, value, messages);
                }
            }
        };
    }

    @Override
    public int compareTo(TaskPropertyInfo o) {
        return propertyName.compareTo(o.getName());
    }
}
