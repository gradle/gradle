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
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

@NonNullApi
public class DefaultTaskClassInfoStore implements TaskClassInfoStore {
    private final CrossBuildInMemoryCache<Class<?>, TaskClassInfo> classInfos;
    private final Transformer<TaskClassInfo, Class<?>> taskClassInfoFactory = new Transformer<TaskClassInfo, Class<?>>() {
        @Override
        public TaskClassInfo transform(Class<?> aClass) {
            return createTaskClassInfo(aClass.asSubclass(Task.class));
        }
    };

    public DefaultTaskClassInfoStore(CrossBuildInMemoryCacheFactory cacheFactory) {
        this.classInfos = cacheFactory.newClassCache();
    }

    @Override
    public TaskClassInfo getTaskClassInfo(Class<? extends Task> type) {
        return classInfos.get(type, taskClassInfoFactory);
    }

    private TaskClassInfo createTaskClassInfo(Class<? extends Task> type) {
        boolean cacheable = type.isAnnotationPresent(CacheableTask.class);
        boolean incremental = false;
        Map<String, Class<?>> processedMethods = Maps.newHashMap();
        ImmutableList.Builder<TaskActionFactory> taskActionFactoriesBuilder = ImmutableList.builder();
        for (Class current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                TaskActionFactory taskActionFactory = createTaskAction(type, method, processedMethods);
                if (taskActionFactory == null) {
                    continue;
                }
                if (taskActionFactory instanceof IncrementalTaskActionFactory) {
                    if (incremental) {
                        throw new GradleException(String.format("Cannot have multiple @TaskAction methods accepting an %s parameter.", IncrementalTaskInputs.class.getSimpleName()));
                    }
                    incremental = true;
                }
                taskActionFactoriesBuilder.add(taskActionFactory);
            }
        }

        return new TaskClassInfo(incremental, taskActionFactoriesBuilder.build(), cacheable);
    }

    @Nullable
    private static TaskActionFactory createTaskAction(Class<? extends Task> taskType, final Method method, Map<String, Class<?>> processedMethods) {
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
            if (!parameterTypes[0].equals(IncrementalTaskInputs.class)) {
                throw new GradleException(String.format(
                    "Cannot use @TaskAction annotation on method %s.%s() because %s is not a valid parameter to an action method.",
                    declaringClass.getSimpleName(), method.getName(), parameterTypes[0]));
            }
            taskActionFactory = new IncrementalTaskActionFactory(taskType, method);
        } else {
            taskActionFactory = new StandardTaskActionFactory(taskType, method);
        }

        Class<?> previousDeclaringClass = processedMethods.put(method.getName(), declaringClass);
        if (previousDeclaringClass == declaringClass) {
            throw new GradleException(String.format(
                "Cannot use @TaskAction annotation on multiple overloads of method %s.%s()",
                declaringClass.getSimpleName(), method.getName()
            ));
        } else if (previousDeclaringClass != null) {
            return null;
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
        public Action<? super Task> create() {
            return new StandardTaskAction(taskType, method);
        }
    }

    private static class IncrementalTaskActionFactory implements TaskActionFactory {
        private final Class<? extends Task> taskType;
        private final Method method;

        public IncrementalTaskActionFactory(Class<? extends Task> taskType, Method method) {
            this.taskType = taskType;
            this.method = method;
        }

        @Override
        public Action<? super Task> create() {
            return new IncrementalTaskAction(taskType, method);
        }
    }
}
