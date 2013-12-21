/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.execution.TaskValidator;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * A {@link ITaskFactory} which determines task actions, inputs and outputs based on annotation attached to the task properties. Also provides some validation based on these annotations.
 */
public class AnnotationProcessingTaskFactory implements ITaskFactory {
    private final ITaskFactory taskFactory;
    private final Map<Class, TaskClassInfo> classInfos;

    private final Transformer<Iterable<File>, Object> filePropertyTransformer = new Transformer<Iterable<File>, Object>() {
        public Iterable<File> transform(Object original) {
            File file = (File) original;
            return file == null ? Collections.<File>emptyList() : Collections.singleton(file);
        }
    };

    private final Transformer<Iterable<File>, Object> iterableFilePropertyTransformer = new Transformer<Iterable<File>, Object>() {
        @SuppressWarnings("unchecked")
        public Iterable<File> transform(Object original) {
            return original != null ? (Iterable<File>) original : Collections.<File>emptyList();
        }
    };

    private final List<? extends PropertyAnnotationHandler> handlers = Arrays.asList(
            new InputFilePropertyAnnotationHandler(),
            new InputDirectoryPropertyAnnotationHandler(),
            new InputFilesPropertyAnnotationHandler(),
            new OutputFilePropertyAnnotationHandler(OutputFile.class, filePropertyTransformer),
            new OutputFilePropertyAnnotationHandler(OutputFiles.class, iterableFilePropertyTransformer),
            new OutputDirectoryPropertyAnnotationHandler(OutputDirectory.class, filePropertyTransformer),
            new OutputDirectoryPropertyAnnotationHandler(OutputDirectories.class, iterableFilePropertyTransformer),
            new InputPropertyAnnotationHandler(),
            new NestedBeanPropertyAnnotationHandler());
    private final ValidationAction notNullValidator = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
            if (value == null) {
                messages.add(String.format("No value has been specified for property '%s'.", propertyName));
            }
        }
    };

    public AnnotationProcessingTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
        this.classInfos = new HashMap<Class, TaskClassInfo>();
    }

    private AnnotationProcessingTaskFactory(Map<Class, TaskClassInfo> classInfos, ITaskFactory taskFactory) {
        this.classInfos = classInfos;
        this.taskFactory = taskFactory;
    }

    public ITaskFactory createChild(ProjectInternal project, Instantiator instantiator) {
        return new AnnotationProcessingTaskFactory(classInfos, taskFactory.createChild(project, instantiator));
    }

    public TaskInternal createTask(Map<String, ?> args) {
        TaskInternal task = taskFactory.createTask(args);
        TaskClassInfo taskClassInfo = getTaskClassInfo(task.getClass());

        // TODO:DAZ Make this more general purpose, and support IncrementalTaskActions added via another mechanism.
        if (taskClassInfo.incremental) {
            // Add a dummy upToDateWhen spec: this will for TaskOutputs.hasOutputs() to be true.
            task.getOutputs().upToDateWhen(new Spec<Task>() {
                public boolean isSatisfiedBy(Task element) {
                    return true;
                }
            });
        }

        for (Factory<Action<Task>> actionFactory : taskClassInfo.taskActions) {
            task.doFirst(actionFactory.create());
        }

        if (taskClassInfo.validator != null) {
            task.doFirst(taskClassInfo.validator);
            taskClassInfo.validator.addInputsAndOutputs(task);
        }

        return task;
    }

    private TaskClassInfo getTaskClassInfo(Class<? extends Task> type) {
        TaskClassInfo taskClassInfo = classInfos.get(type);
        if (taskClassInfo == null) {
            taskClassInfo = new TaskClassInfo();
            findTaskActions(type, taskClassInfo);

            Validator validator = new Validator();
            validator.attachActions(null, type);

            if (!validator.properties.isEmpty()) {
                taskClassInfo.validator = validator;
            }
            classInfos.put(type, taskClassInfo);
        }
        return taskClassInfo;
    }

    private void findTaskActions(Class<? extends Task> type, TaskClassInfo taskClassInfo) {
        Set<String> methods = new HashSet<String>();
        for (Class current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                attachTaskAction(method, taskClassInfo, methods);
            }
        }
    }

    private void attachTaskAction(final Method method, TaskClassInfo taskClassInfo, Collection<String> processedMethods) {
        if (method.getAnnotation(TaskAction.class) == null) {
            return;
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new GradleException(String.format("Cannot use @TaskAction annotation on static method %s.%s().",
                    method.getDeclaringClass().getSimpleName(), method.getName()));
        }
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1) {
            throw new GradleException(String.format(
                    "Cannot use @TaskAction annotation on method %s.%s() as this method takes multiple parameters.",
                    method.getDeclaringClass().getSimpleName(), method.getName()));
        }

        if (parameterTypes.length == 1) {
            if (!parameterTypes[0].equals(IncrementalTaskInputs.class)) {
                throw new GradleException(String.format(
                        "Cannot use @TaskAction annotation on method %s.%s() because %s is not a valid parameter to an action method.",
                        method.getDeclaringClass().getSimpleName(), method.getName(), parameterTypes[0]));
            }
            if (taskClassInfo.incremental) {
                throw new GradleException(String.format("Cannot have multiple @TaskAction methods accepting an %s parameter.", IncrementalTaskInputs.class.getSimpleName()));
            }
            taskClassInfo.incremental = true;
        }
        if (processedMethods.contains(method.getName())) {
            return;
        }
        taskClassInfo.taskActions.add(createActionFactory(method, parameterTypes));
        processedMethods.add(method.getName());
    }

    private Factory<Action<Task>> createActionFactory(final Method method, final Class<?>[] parameterTypes) {
        return new Factory<Action<Task>>() {
            public Action<Task> create() {
                if (parameterTypes.length == 1) {
                    return new IncrementalTaskAction(method);
                } else {
                    return new StandardTaskAction(method);
                }
            }
        };
    }

    private static boolean isGetter(Method method) {
        return ((method.getName().startsWith("get") && method.getReturnType() != Void.TYPE)
                || (method.getName().startsWith("is") && method.getReturnType().equals(boolean.class)))
                && method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers());
    }

    private static class StandardTaskAction implements Action<Task> {
        private final Method method;

        public StandardTaskAction(Method method) {
            this.method = method;
        }

        public void execute(Task task) {
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(method.getDeclaringClass().getClassLoader());
            try {
                doExecute(task, method.getName());
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        protected void doExecute(Task task, String methodName) {
            JavaReflectionUtil.method(task, Object.class, methodName).invoke(task);
        }
    }

    public static class IncrementalTaskAction extends StandardTaskAction implements ContextAwareTaskAction {

        private TaskArtifactState taskArtifactState;

        public IncrementalTaskAction(Method method) {
            super(method);
        }

        public void contextualise(TaskExecutionContext context) {
            this.taskArtifactState = context == null ? null : context.getTaskArtifactState();
        }

        protected void doExecute(Task task, String methodName) {
            JavaReflectionUtil.method(task, Object.class, methodName, IncrementalTaskInputs.class).invoke(task, taskArtifactState.getInputChanges());
            taskArtifactState = null;
        }
    }

    private static class TaskClassInfo {
        public Validator validator;
        public List<Factory<Action<Task>>> taskActions = new ArrayList<Factory<Action<Task>>>();
        public boolean incremental;
    }

    private class Validator implements Action<Task>, TaskValidator {
        private Set<PropertyInfo> properties = new LinkedHashSet<PropertyInfo>();

        public void addInputsAndOutputs(final TaskInternal task) {
            task.addValidator(this);
            for (final PropertyInfo property : properties) {
                Callable<Object> futureValue = new Callable<Object>() {
                    public Object call() throws Exception {
                        return property.getValue(task).getValue();
                    }
                };

                property.configureAction.update(task, futureValue);
            }
        }

        public void execute(Task task) {
        }

        public void validate(TaskInternal task, Collection<String> messages) {
            List<PropertyValue> propertyValues = new ArrayList<PropertyValue>();
            for (PropertyInfo property : properties) {
                propertyValues.add(property.getValue(task));
            }
            for (PropertyValue propertyValue : propertyValues) {
                propertyValue.checkNotNull(messages);
            }
            for (PropertyValue propertyValue : propertyValues) {
                propertyValue.checkValid(messages);
            }
        }

        public void attachActions(PropertyInfo parent, Class<?> type) {
            Class<?> superclass = type.getSuperclass();
            if (!(superclass == null
                    // Avoid reflecting on classes we know we don't need to look at
                    || superclass.equals(ConventionTask.class) || superclass.equals(DefaultTask.class)
                    || superclass.equals(AbstractTask.class) || superclass.equals(Object.class)
            )) {
                attachActions(parent, superclass);
            }

            for (Method method : type.getDeclaredMethods()) {
                if (!isGetter(method)) {
                    continue;
                }

                String name = method.getName();
                int prefixLength = name.startsWith("is") ? 2 : 3; // it's 'get' if not 'is'.
                String fieldName = StringUtils.uncapitalize(name.substring(prefixLength));
                String propertyName = fieldName;
                if (parent != null) {
                    propertyName = parent.getName() + '.' + propertyName;
                }
                PropertyInfo propertyInfo = new PropertyInfo(type, this, parent, propertyName, method);

                attachValidationActions(propertyInfo, fieldName);

                if (propertyInfo.required) {
                    properties.add(propertyInfo);
                }
            }
        }

        private void attachValidationActions(PropertyInfo propertyInfo, String fieldName) {
            for (PropertyAnnotationHandler handler : handlers) {
                attachValidationAction(handler, propertyInfo, fieldName);
            }
        }

        private void attachValidationAction(PropertyAnnotationHandler handler, PropertyInfo propertyInfo, String fieldName) {
            final Method method = propertyInfo.method;
            Class<? extends Annotation> annotationType = handler.getAnnotationType();

            AnnotatedElement annotationTarget = null;
            if (method.getAnnotation(annotationType) != null) {
                annotationTarget = method;
            } else {
                try {
                    Field field = method.getDeclaringClass().getDeclaredField(fieldName);
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

            propertyInfo.attachActions(handler);
        }
    }

    private interface PropertyValue {
        Object getValue();

        void checkNotNull(Collection<String> messages);

        void checkValid(Collection<String> messages);
    }

    private static class PropertyInfo implements PropertyActionContext {
        private static final ValidationAction NO_OP_VALIDATION_ACTION = new ValidationAction() {
            public void validate(String propertyName, Object value, Collection<String> messages) {
            }
        };
        private static final PropertyValue NO_OP_VALUE = new PropertyValue() {
            public Object getValue() {
                return null;
            }

            public void checkNotNull(Collection<String> messages) {
            }

            public void checkValid(Collection<String> messages) {
            }
        };
        private static final UpdateAction NO_OP_CONFIGURATION_ACTION = new UpdateAction() {
            public void update(Task task, Callable<Object> futureValue) {
            }
        };

        private final Validator validator;
        private final PropertyInfo parent;
        private final String propertyName;
        private final Method method;
        private ValidationAction validationAction = NO_OP_VALIDATION_ACTION;
        private ValidationAction notNullValidator = NO_OP_VALIDATION_ACTION;
        private UpdateAction configureAction = NO_OP_CONFIGURATION_ACTION;
        public boolean required;
        private final Class<?> type;

        private PropertyInfo(Class<?> type, Validator validator, PropertyInfo parent, String propertyName, Method method) {
            this.type = type;
            this.validator = validator;
            this.parent = parent;
            this.propertyName = propertyName;
            this.method = method;
        }

        @Override
        public String toString() {
            return propertyName;
        }

        public String getName() {
            return propertyName;
        }

        public Class<?> getType() {
            return method.getReturnType();
        }

        public Class<?> getInstanceVariableType() {
            Class<?> currentType = type;
            while (!currentType.equals(Object.class)) {
                try {
                    return currentType.getDeclaredField(propertyName).getType();
                } catch (NoSuchFieldException e) {
                    currentType = currentType.getSuperclass();
                }
            }

            return null;
        }

        public AnnotatedElement getTarget() {
            return method;
        }

        public void setValidationAction(ValidationAction action) {
            validationAction = action;
        }

        public void setConfigureAction(UpdateAction action) {
            configureAction = action;
        }

        public void setNotNullValidator(ValidationAction notNullValidator) {
            this.notNullValidator = notNullValidator;
        }

        public void attachActions(Class<?> type) {
            validator.attachActions(this, type);
        }

        public PropertyValue getValue(Object rootObject) {
            Object bean = rootObject;
            if (parent != null) {
                PropertyValue parentValue = parent.getValue(rootObject);
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

            return new PropertyValue() {
                public Object getValue() {
                    return value;
                }

                public void checkNotNull(Collection<String> messages) {
                    notNullValidator.validate(propertyName, value, messages);
                }

                public void checkValid(Collection<String> messages) {
                    if (value != null) {
                        validationAction.validate(propertyName, value, messages);
                    }
                }
            };
        }

        public void attachActions(PropertyAnnotationHandler handler) {
            handler.attachActions(this);
            required = true;
        }
    }
}
