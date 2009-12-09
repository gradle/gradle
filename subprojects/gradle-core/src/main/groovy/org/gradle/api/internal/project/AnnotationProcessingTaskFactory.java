/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.ReflectionUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * A {@link ITaskFactory} which determines task actions, inputs and outputs based on annotation attached to the task
 * properties. Also provides some validation based on these annotations.
 */
public class AnnotationProcessingTaskFactory implements ITaskFactory {
    private final ITaskFactory taskFactory;
    private final Map<Class, List<Action<Task>>> actionsForType = new HashMap<Class, List<Action<Task>>>();
    private final List<? extends PropertyAnnotationHandler> handlers = Arrays.asList(
            new InputFilePropertyAnnotationHandler(), new InputDirectoryPropertyAnnotationHandler(),
            new InputFilesPropertyAnnotationHandler(), new OutputFilePropertyAnnotationHandler(),
            new OutputDirectoryPropertyAnnotationHandler());
    private final ValidationAction notNullValidator = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            if (value == null) {
                throw new InvalidUserDataException(String.format("No value has been specified for property '%s'.",
                        propertyName));
            }
        }
    };

    public AnnotationProcessingTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public TaskInternal createTask(ProjectInternal project, Map<String, ?> args) {
        TaskInternal task = taskFactory.createTask(project, args);

        Class<? extends Task> type = task.getClass();
        List<Action<Task>> actions = actionsForType.get(type);
        if (actions == null) {
            actions = createActionsForType(type);
            actionsForType.put(type, actions);
        }


        for (Action<Task> action : actions) {
            task.doFirst(action);
            if (action instanceof Validator) {
                Validator validator = (Validator) action;
                validator.addInputsAndOutputs(task);
            }
        }

        return task;
    }

    private List<Action<Task>> createActionsForType(Class<? extends Task> type) {
        List<Action<Task>> actions;
        actions = new ArrayList<Action<Task>>();
        Validator validator = new Validator();
        for (Class current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                attachTaskAction(method, actions);
                if (!isGetter(method)) {
                    continue;
                }

                final String propertyName = StringUtils.uncapitalize(method.getName().substring(3));
                PropertyInfo propertyInfo = new PropertyInfo(propertyName, method);

                attachValidationActions(propertyInfo);

                if (propertyInfo.required) {
                    validator.properties.add(propertyInfo);
                }
            }
        }

        if (!validator.properties.isEmpty()) {
            actions.add(validator);
        }
        return actions;
    }

    private void attachTaskAction(final Method method, Collection<Action<Task>> actions) {
        if (method.getAnnotation(TaskAction.class) == null) {
            return;
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new GradleException(String.format("Cannot use @TaskAction annotation on static method %s.%s().",
                    method.getDeclaringClass().getSimpleName(), method.getName()));
        }
        if (method.getParameterTypes().length > 0) {
            throw new GradleException(String.format(
                    "Cannot use @TaskAction annotation on method %s.%s() as this method takes parameters.",
                    method.getDeclaringClass().getSimpleName(), method.getName()));
        }
        actions.add(new Action<Task>() {
            public void execute(Task task) {
                ReflectionUtil.invoke(task, method.getName(), new Object[0]);
            }
        });
    }

    private void attachValidationActions(PropertyInfo propertyInfo) {
        for (PropertyAnnotationHandler handler : handlers) {
            attachValidationAction(handler, propertyInfo);
        }
    }

    private void attachValidationAction(PropertyAnnotationHandler handler, PropertyInfo propertyInfo) {
        final Method method = propertyInfo.method;
        Class<? extends Annotation> annotationType = handler.getAnnotationType();
        if (!isGetter(method)) {
            if (method.getAnnotation(annotationType) != null) {
                throw new GradleException(String.format("Cannot attach @%s to non-getter method %s().",
                        annotationType.getSimpleName(), method.getName()));
            }
            return;
        }

        AnnotatedElement annotationTarget = null;
        if (method.getAnnotation(annotationType) != null) {
            annotationTarget = method;
        } else {
            try {
                Field field = method.getDeclaringClass().getDeclaredField(propertyInfo.propertyName);
                if (field.getAnnotation(annotationType) != null) {
                    annotationTarget = field;
                }
            } catch (NoSuchFieldException e) {
                // ok - ignore
            }
        }
        if (annotationTarget == null) {
            return;
        }

        Annotation optional = annotationTarget.getAnnotation(Optional.class);
        if (optional == null) {
            propertyInfo.setNotNullValidator(notNullValidator);
        }

        propertyInfo.setActions(handler.getActions(annotationTarget, propertyInfo.propertyName));
    }

    private boolean isGetter(Method method) {
        return method.getName().startsWith("get") && method.getReturnType() != Void.TYPE
                && method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers());
    }

    private static class Validator implements Action<Task> {
        private Set<PropertyInfo> properties = new LinkedHashSet<PropertyInfo>();

        public void addInputsAndOutputs(final Task task) {
            for (final PropertyInfo property : properties) {
                Callable<Object> futureValue = new Callable<Object>() {
                    public Object call() throws Exception {
                        return ReflectionUtil.invoke(task, property.method.getName(), new Object[0]);
                    }
                };

                property.actions.attachInputs(task.getInputs(), futureValue);
                property.actions.attachOutputs(task.getOutputs(), futureValue);
            }
        }

        public void execute(Task task) {
            try {
                Map<PropertyInfo, Object> propertyValues = new HashMap<PropertyInfo, Object>();
                for (PropertyInfo property : properties) {
                    propertyValues.put(property, ReflectionUtil.invoke(task, property.method.getName(), new Object[0]));
                }
                for (PropertyInfo property : properties) {
                    property.notNullValidator.validate(property.propertyName, propertyValues.get(property));
                }
                for (PropertyInfo property : properties) {
                    property.skipAction.validate(property.propertyName, propertyValues.get(property));
                }
                for (PropertyInfo property : properties) {
                    Object value = propertyValues.get(property);
                    if (value != null) {
                        property.validationAction.validate(property.propertyName, value);
                    }
                }
            } catch (InvalidUserDataException e) {
                throw new InvalidUserDataException(String.format("Error validating %s: %s", task, e.getMessage()), e);
            }
        }
    }

    private static class PropertyInfo {
        private final ValidationAction noOpAction = new ValidationAction() {
            public void validate(String propertyName, Object value) throws InvalidUserDataException {
            }
        };

        private final String propertyName;
        private final Method method;
        private PropertyAnnotationHandler.PropertyActions actions;
        private ValidationAction skipAction = noOpAction;
        private ValidationAction validationAction = noOpAction;
        private ValidationAction notNullValidator = noOpAction;
        public boolean required;

        private PropertyInfo(String propertyName, Method method) {
            this.propertyName = propertyName;
            this.method = method;
        }

        public void setActions(PropertyAnnotationHandler.PropertyActions actions) {
            this.actions = actions;
            final ValidationAction skipAction = actions.getSkipAction();
            if (skipAction != null) {
                this.skipAction = skipAction;
            }

            final ValidationAction validationAction = actions.getValidationAction();
            if (validationAction != null) {
                this.validationAction = validationAction;
            }
            required = true;
        }

        public void setNotNullValidator(ValidationAction notNullValidator) {
            this.notNullValidator = notNullValidator;
        }
    }
}
