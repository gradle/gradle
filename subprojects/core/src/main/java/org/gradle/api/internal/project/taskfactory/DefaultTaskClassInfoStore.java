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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.work.InputChanges;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NonNullApi
public class DefaultTaskClassInfoStore implements TaskClassInfoStore {
    private final CrossBuildInMemoryCache<Class<?>, TaskClassInfo> classInfos;
    private final Function<Class<?>, TaskClassInfo> taskClassInfoFactory = aClass -> createTaskClassInfo(aClass.asSubclass(Task.class));

    public DefaultTaskClassInfoStore(CrossBuildInMemoryCacheFactory cacheFactory) {
        this.classInfos = cacheFactory.newClassCache();
    }

    @Override
    public TaskClassInfo getTaskClassInfo(Class<? extends Task> type) {
        return classInfos.get(type, taskClassInfoFactory);
    }

    private TaskClassInfo createTaskClassInfo(Class<? extends Task> type) {
        boolean cacheable = type.isAnnotationPresent(CacheableTask.class);
        Optional<String> reasonNotToTrackState = Optional.ofNullable(type.getAnnotation(UntrackedTask.class))
            .map(UntrackedTask::because);
        Map<String, Class<?>> processedMethods = Maps.newHashMap();
        ImmutableList.Builder<TaskActionFactory> taskActionFactoriesBuilder = ImmutableList.builder();
        AbstractIncrementalTaskActionFactory foundIncrementalTaskActionFactory = null;
        for (Class current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                TaskActionFactory taskActionFactory = createTaskAction(type, method);
                if (taskActionFactory == null) {
                    continue;
                }
                Class<?> declaringClass = method.getDeclaringClass();
                Class<?> previousDeclaringClass = processedMethods.put(method.getName(), declaringClass);
                if (taskActionFactory instanceof AbstractIncrementalTaskActionFactory
                    && foundIncrementalTaskActionFactory != null
                    && method.getName().equals(foundIncrementalTaskActionFactory.getMethod().getName())
                ) {
                    // Let's try if we can decide on one
                    AbstractIncrementalTaskActionFactory selectedTaskAction = selectIncrementalTaskAction(type, foundIncrementalTaskActionFactory, taskActionFactory, current, method);
                    if (selectedTaskAction != null) {
                        foundIncrementalTaskActionFactory = selectedTaskAction;
                        continue;
                    }
                }
                if (previousDeclaringClass == declaringClass) {
                    throw new GradleException(String.format(
                        "Cannot use @TaskAction annotation on multiple overloads of method %s.%s()",
                        declaringClass.getSimpleName(), method.getName()
                    ));
                } else if (previousDeclaringClass != null) {
                    continue;
                }
                if (taskActionFactory instanceof AbstractIncrementalTaskActionFactory) {
                    if (foundIncrementalTaskActionFactory != null) {
                        @SuppressWarnings("deprecation")
                        Class<?> incrementalTaskInputsClass = org.gradle.api.tasks.incremental.IncrementalTaskInputs.class;
                        throw new GradleException(String.format("Cannot have multiple @TaskAction methods accepting an %s or %s parameter.", InputChanges.class.getSimpleName(), incrementalTaskInputsClass.getSimpleName()));
                    }
                    foundIncrementalTaskActionFactory = (AbstractIncrementalTaskActionFactory) taskActionFactory;
                    continue;
                }
                taskActionFactoriesBuilder.add(taskActionFactory);
            }
        }
        if (foundIncrementalTaskActionFactory != null) {
            taskActionFactoriesBuilder.add(foundIncrementalTaskActionFactory);
        }

        return new TaskClassInfo(taskActionFactoriesBuilder.build(), cacheable, reasonNotToTrackState);
    }

    /**
     * Select between two incremental task action factories.
     *
     * The selection works like this:
     * - If both task actions are of the same type (InputChanges or IncrementalTaskInputs), we select the one we already found.
     *   This happens when a subclass overrides the task action method in the subclass, while both have TaskAction annotations.
     * - If the InputChanges is not deprecated and the IncrementalTaskInputs method is, then we try to select one:
     *   - If a subclass overrides the IncrementalTaskInputs method, we create a {@link BridgingIncrementalInputsTaskActionFactory} which passes the {@link InputChanges} object into the IncrementalTaskInputs method
     *   - If no subclass overrides the IncrementalTaskInputs method, we use the InputChanges method directly.
     *
     *   All this is only required to support the Android Gradle plugin &lt; 3.6.
     *   As soon as 3.6 is out we should drop the support for 3.5 and simplify the code here again.
     */
    @Nullable
    private AbstractIncrementalTaskActionFactory selectIncrementalTaskAction(Class<? extends Task> taskClass, AbstractIncrementalTaskActionFactory foundTaskActionFactory, TaskActionFactory currentTaskActionFactory, Class currentClass, Method currentMethod) {
        if (currentTaskActionFactory.getClass() == foundTaskActionFactory.getClass()) {
            return foundTaskActionFactory;
        }
        AbstractIncrementalTaskActionFactory currentIncrementalTaskActionFactory = (AbstractIncrementalTaskActionFactory) currentTaskActionFactory;
        Map<Boolean, List<AbstractIncrementalTaskActionFactory>> partitionedActions = Stream.of(foundTaskActionFactory, currentIncrementalTaskActionFactory)
            .collect(Collectors.partitioningBy(IncrementalInputsTaskActionFactory.class::isInstance));
        List<AbstractIncrementalTaskActionFactory> incrementalInputsFactories = partitionedActions.get(true);
        List<AbstractIncrementalTaskActionFactory> incrementalTaskInputFactories = partitionedActions.get(false);

        if (incrementalInputsFactories.size() == 1 && incrementalTaskInputFactories.size() == 1) {
            IncrementalInputsTaskActionFactory incrementalInputsTaskActionFactory = (IncrementalInputsTaskActionFactory) Iterables.getOnlyElement(incrementalInputsFactories);
            IncrementalTaskInputsTaskActionFactory incrementalTaskInputsTaskActionFactory = (IncrementalTaskInputsTaskActionFactory) Iterables.getOnlyElement(incrementalTaskInputFactories);

            if (isDeprecated(incrementalTaskInputsTaskActionFactory) && !isDeprecated(incrementalInputsTaskActionFactory)) {
                Class<?> declaringClassForIncrementalTaskInputsMethod = getDeclaringClassForIncrementalTaskInputsMethod(taskClass, currentMethod.getName());
                if (declaringClassForIncrementalTaskInputsMethod != currentClass) {
                    return new BridgingIncrementalInputsTaskActionFactory(taskClass, currentMethod);
                } else {
                    return incrementalInputsTaskActionFactory;
                }
            }
        }
        return null;
    }

    private Class<?> getDeclaringClassForIncrementalTaskInputsMethod(Class<? extends Task> type, String methodName) {
        Method incrementalTaskInputsMethod = null;
        Class<?> current = type;
        while (incrementalTaskInputsMethod == null) {
            incrementalTaskInputsMethod = getIncrementalTaskInputsMethod(current, methodName);
            current = current.getSuperclass();
        }
        return incrementalTaskInputsMethod.getDeclaringClass();
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private Method getIncrementalTaskInputsMethod(Class<?> type, String methodName) {
        try {
            return type.getDeclaredMethod(methodName, org.gradle.api.tasks.incremental.IncrementalTaskInputs.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private boolean isDeprecated(AbstractIncrementalTaskActionFactory foundIncrementalTaskActionFactory) {
        return foundIncrementalTaskActionFactory.getMethod().getAnnotation(Deprecated.class) != null;
    }

    @Nullable
    private static TaskActionFactory createTaskAction(Class<? extends Task> taskType, final Method method) {
        if (method.getAnnotation(TaskAction.class) == null) {
            return null;
        }
        Class<?> declaringClass = method.getDeclaringClass();
        if (Modifier.isStatic(method.getModifiers())) {
            throw new GradleException(String.format("Cannot use @TaskAction annotation on static method %s.%s().",
                declaringClass.getSimpleName(), method.getName()));
        }
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1) {
            throw new GradleException(String.format(
                "Cannot use @TaskAction annotation on method %s.%s() as this method takes multiple parameters.",
                declaringClass.getSimpleName(), method.getName()));
        }

        TaskActionFactory taskActionFactory;
        if (parameterTypes.length == 1) {
            Class<?> parameterType = parameterTypes[0];
            @SuppressWarnings("deprecation")
            Class<?> incrementalTaskInputsClass = org.gradle.api.tasks.incremental.IncrementalTaskInputs.class;
            if (parameterType.equals(incrementalTaskInputsClass)) {
                taskActionFactory = new IncrementalTaskInputsTaskActionFactory(taskType, method);
            } else if (parameterType.equals(InputChanges.class)) {
                taskActionFactory = new IncrementalInputsTaskActionFactory(taskType, method);
            } else {
                throw new GradleException(String.format(
                    "Cannot use @TaskAction annotation on method %s.%s() because %s is not a valid parameter to an action method.",
                    declaringClass.getSimpleName(), method.getName(), parameterType));
            }
        } else {
            taskActionFactory = new StandardTaskActionFactory(taskType, method);
        }

        return taskActionFactory;
    }

    private static class StandardTaskActionFactory implements TaskActionFactory {
        private final Class<? extends Task> taskType;
        private final Method method;

        public StandardTaskActionFactory(Class<? extends Task> taskType, Method method) {
            this.taskType = taskType;
            this.method = method;
        }

        @Override
        public Action<? super Task> create(Instantiator instantiator) {
            return new StandardTaskAction(taskType, method);
        }
    }

    private static class IncrementalInputsTaskActionFactory extends AbstractIncrementalTaskActionFactory {
        public IncrementalInputsTaskActionFactory(Class<? extends Task> taskType, Method method) {
            super(taskType, method);
        }

        @Override
        protected Action<? super Task> doCreate(Instantiator instantiator, Class<? extends Task> taskType, Method method) {
            return new IncrementalInputsTaskAction(taskType, method);
        }
    }

    private static class IncrementalTaskInputsTaskActionFactory extends AbstractIncrementalTaskActionFactory {
        public IncrementalTaskInputsTaskActionFactory(Class<? extends Task> taskType, Method method) {
            super(taskType, method);
        }

        @Override
        protected Action<? super Task> doCreate(Instantiator instantiator, Class<? extends Task> taskType, Method method) {
            Class<?> declaringClass = method.getDeclaringClass();
            DeprecationLogger.deprecateIndirectUsage("IncrementalTaskInputs")
                .withContext("On method '" + declaringClass.getSimpleName() + "." + method.getName() + "'")
                .withAdvice("use 'org.gradle.work.InputChanges' instead.")
                .willBeRemovedInGradle8()
                .withUpgradeGuideSection(7, "incremental_task_inputs_deprecation")
                .nagUser();
            return new IncrementalTaskInputsTaskAction(instantiator, taskType, method);
        }
    }

    private static class BridgingIncrementalInputsTaskActionFactory extends AbstractIncrementalTaskActionFactory {

        public BridgingIncrementalInputsTaskActionFactory(Class<? extends Task> taskType, Method method) {
            super(taskType, method);
        }

        @Override
        protected Action<? super Task> doCreate(Instantiator instantiator, Class<? extends Task> taskType, Method method) {
            return new BridgingIncrementalInputsTaskAction(taskType, method);
        }
    }

    private static abstract class AbstractIncrementalTaskActionFactory implements TaskActionFactory {
        private final Class<? extends Task> taskType;
        private final Method method;

        public AbstractIncrementalTaskActionFactory(Class<? extends Task> taskType, Method method) {
            this.taskType = taskType;
            this.method = method;
        }

        public Method getMethod() {
            return method;
        }

        protected abstract Action<? super Task> doCreate(Instantiator instantiator, Class<? extends Task> taskType, Method method);

        @Override
        public Action<? super Task> create(Instantiator instantiator) {
            return doCreate(instantiator, taskType, method);
        }
    }
}
