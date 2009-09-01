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

import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.ReflectionUtil;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.annotation.Annotation;
import java.io.File;

public class AnnotationProcessingTaskFactory implements ITaskFactory {
    private final ValidationAction inputFileValidation = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = (File) value;
            if (!fileValue.exists()) {
                throw new InvalidUserDataException(String.format(
                        "File '%s' specified for property '%s' does not exist.", fileValue, propertyName));
            }
            if (!fileValue.isFile()) {
                throw new InvalidUserDataException(String.format(
                        "File '%s' specified for property '%s' is not a file.", fileValue, propertyName));
            }
        }
    };
    private final ValidationAction skipEmptyFileCollection = new ValidationAction() {
            public void validate(String propertyName, Object value) throws InvalidUserDataException {
                if (value instanceof FileCollection) {
                    ((FileCollection) value).stopExecutionIfEmpty();
                }
            }
        };
    private final ValidationAction inputDirValidation = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = (File) value;
            if (!fileValue.exists()) {
                throw new InvalidUserDataException(String.format(
                        "Directory '%s' specified for property '%s' does not exist.", fileValue, propertyName));
            }
            if (!fileValue.isDirectory()) {
                throw new InvalidUserDataException(String.format(
                        "Directory '%s' specified for property '%s' is not a directory.", fileValue, propertyName));
            }
        }
    };
    private final ValidationAction ouputFileValidation = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = (File) value;
            if (fileValue.exists() && !fileValue.isFile()) {
                throw new InvalidUserDataException(String.format(
                        "Cannot write to file '%s' specified for property '%s' as it is a directory.", fileValue, propertyName));
            }
            if (!fileValue.getParentFile().isDirectory() && !fileValue.getParentFile().mkdirs()) {
                throw new InvalidUserDataException(String.format(
                        "Cannot create parent directory '%s' of file specified for property '%s'.", fileValue.getParentFile(), propertyName));
            }
        }
    };
    private final ValidationAction outputDirValidation = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = (File) value;
            if (!fileValue.isDirectory() && !fileValue.mkdirs()) {
                throw new InvalidUserDataException(String.format(
                        "Cannot create directory '%s' specified for property '%s'.", fileValue, propertyName));
            }
        }
    };
    private final ITaskFactory taskFactory;
    private final Map<Class, List<Action<Task>>> actionsForType = new HashMap<Class, List<Action<Task>>>();

    public AnnotationProcessingTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public Task createTask(Project project, Map args) {
        Task task = taskFactory.createTask(project, args);
        List<Action<Task>> actions = actionsForType.get(task.getClass());
        if (actions == null) {
            List<Action<Task>> notNullActions = new ArrayList<Action<Task>>();
            List<Action<Task>> skipActions = new ArrayList<Action<Task>>();
            List<Action<Task>> validationActions = new ArrayList<Action<Task>>();
            actions = new ArrayList<Action<Task>>();

            for (Class current = task.getClass(); current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    attachTaskAction(method, actions);
                    MethodInfo methodInfo = new MethodInfo(method, skipActions, validationActions, notNullActions);
                    attachInputFileValidation(methodInfo);
                    attachInputFilesValidation(methodInfo);
                    attachInputDirValidation(methodInfo);
                    attachOutputFileValidation(methodInfo);
                    attachOutputDirValidation(methodInfo);
                }
            }

            actions.addAll(validationActions);
            actions.addAll(skipActions);
            actions.addAll(notNullActions);
            actionsForType.put(task.getClass(), actions);
        }

        for (Action<Task> action : actions) {
            task.doFirst(action);
        }

        return task;
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

    private void attachInputFileValidation(MethodInfo methodInfo) {
        attachValidationAction(methodInfo, InputFile.class, inputFileValidation);
    }

    private void attachInputFilesValidation(MethodInfo methodInfo) {
        ValidationAction skipAction = null;
        if (methodInfo.method.getAnnotation(SkipWhenEmpty.class) != null) {
            skipAction = skipEmptyFileCollection;
        }
        attachValidationAction(methodInfo, InputFiles.class, null, skipAction);
    }

    private void attachInputDirValidation(MethodInfo methodInfo) {
        attachValidationAction(methodInfo, InputDirectory.class, inputDirValidation);
    }

    private void attachOutputFileValidation(MethodInfo methodInfo) {
        attachValidationAction(methodInfo, OutputFile.class, ouputFileValidation);
    }

    private void attachOutputDirValidation(MethodInfo methodInfo) {
        attachValidationAction(methodInfo, OutputDirectory.class, outputDirValidation);
    }

    private void attachValidationAction(MethodInfo methodInfo, Class annotationType, ValidationAction validationAction) {
        attachValidationAction(methodInfo, annotationType, validationAction, null);
    }

    private void attachValidationAction(MethodInfo methodInfo, Class annotationType, final ValidationAction validationAction, final ValidationAction skipAction) {
        final Method method = methodInfo.method;
        if (method.getAnnotation(annotationType) == null) {
            return;
        }
        if (!isGetter(method)) {
            throw new GradleException(String.format("Cannot attach @%s to non-getter method %s().",
                    annotationType.getSimpleName(), method.getName()));
        }

        final String propertyName = StringUtils.uncapitalize(method.getName().substring(3));
        Annotation optional = method.getAnnotation(Optional.class);
        if (optional == null) {
            methodInfo.notNullActions.add(new Action<Task>() {
                public void execute(Task task) {
                    Object value = ReflectionUtil.invoke(task, method.getName(), new Object[0]);
                    if (value == null) {
                        throw new InvalidUserDataException(String.format(
                                "No value has been specified for property '%s'.", propertyName));
                    }
                }
            });
        }

        if (skipAction != null) {
            methodInfo.skipActions.add(new Action<Task>() {
                public void execute(Task task) {
                    Object value = ReflectionUtil.invoke(task, method.getName(), new Object[0]);
                    skipAction.validate(propertyName, value);
                }
            });
        }

        if (validationAction != null) {
            methodInfo.validationActions.add(new Action<Task>() {
                public void execute(Task task) {
                    Object value = ReflectionUtil.invoke(task, method.getName(), new Object[0]);
                    if (value != null) {
                        validationAction.validate(propertyName, value);
                    }
                }
            });
        }
    }

    private boolean isGetter(Method method) {
        return method.getName().startsWith("get") && method.getReturnType() != Void.TYPE
                && method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers());
    }

    private interface ValidationAction {
        void validate(String propertyName, Object value) throws InvalidUserDataException;
    }
    
    private static class MethodInfo {
        private final Method method;
        private final List<Action<Task>> skipActions;
        private final List<Action<Task>> validationActions;
        private final List<Action<Task>> notNullActions;

        public MethodInfo(Method method, List<Action<Task>> skipActions, List<Action<Task>> validationActions, List<Action<Task>> notNullActions) {
            this.method = method;
            this.skipActions = skipActions;
            this.validationActions = validationActions;
            this.notNullActions = notNullActions;
        }
    }

}
