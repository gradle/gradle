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

import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskPropertyValue;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.DeferredUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TaskPropertyInfo implements Comparable<TaskPropertyInfo> {
    private static final UpdateAction NO_OP_CONFIGURATION_ACTION = new UpdateAction() {
        @Override
        public void update(TaskInternal task, TaskPropertyValue futureValue) {
        }
    };

    private final TaskPropertyInfo parent;
    private final String propertyName;
    private final Class<? extends Annotation> propertyType;
    private final Method method;
    private final UpdateAction configureAction;

    TaskPropertyInfo(TaskPropertyInfo parent, String propertyName, Class<? extends Annotation> propertyType, Method method, UpdateAction configureAction) {
        this.parent = parent;
        this.propertyName = propertyName;
        this.propertyType = propertyType;
        this.method = method;
        method.setAccessible(true);
        this.configureAction = configureAction == null ? NO_OP_CONFIGURATION_ACTION : configureAction;
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

    public PropertyValue getValue(Object rootObject) {
        final Object bean;
        if (parent != null) {
            PropertyValue parentValue = parent.getValue(rootObject);
            if (parentValue.getValue() == null) {
                return PropertyValue.NO_OP;
            }
            bean = parentValue.getValue();
        } else {
            bean = rootObject;
        }

        final Object value = DeprecationLogger.whileDisabled(new Factory<Object>() {
            public Object create() {
                try {
                    return method.invoke(bean);
                } catch (InvocationTargetException e) {
                    throw UncheckedException.throwAsUncheckedException(e.getCause());
                } catch (Exception e) {
                    throw new GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), bean), e);
                }
            }
        });

        return new PropertyValue() {
            @Override
            public Object getValue() {
                return value instanceof Provider ? ((Provider<?>) value).getOrNull() : value;
            }

            @Override
            public void validate(boolean optional, ValidationAction valueValidator, TaskValidationContext context, TaskValidationContext.Severity severity) {
                Object unpacked = DeferredUtil.unpack(getValue());
                if (unpacked == null) {
                    if (!optional) {
                        context.recordValidationMessage(severity, String.format("No value has been specified for property '%s'.", propertyName));
                    }
                } else {
                    valueValidator.validate(propertyName, unpacked, context, severity);
                }
            }
        };
    }

    @Override
    public int compareTo(@Nonnull TaskPropertyInfo o) {
        return propertyName.compareTo(o.getName());
    }

    public interface PropertyValue {
        PropertyValue NO_OP = new PropertyValue() {
            @Override
            public Object getValue() {
                return null;
            }

            @Override
            public void validate(boolean optional, ValidationAction valueValidator, TaskValidationContext context, TaskValidationContext.Severity severity) {
            }
        };

        Object getValue();

        void validate(boolean optional, ValidationAction valueValidator, TaskValidationContext context, TaskValidationContext.Severity severity);
    }
}
