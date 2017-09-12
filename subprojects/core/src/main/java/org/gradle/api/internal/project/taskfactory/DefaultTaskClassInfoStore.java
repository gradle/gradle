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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public class DefaultTaskClassInfoStore implements TaskClassInfoStore {
    private final TaskClassValidatorExtractor validatorExtractor;

    private final LoadingCache<Class<? extends Task>, TaskClassInfo> classInfos = CacheBuilder.newBuilder()
        .weakKeys()
        .build(new CacheLoader<Class<? extends Task>, TaskClassInfo>() {
            @Override
            public TaskClassInfo load(@Nonnull Class<? extends Task> type) throws Exception {
                return createTaskClassInfo(type);
            }
        });

    public DefaultTaskClassInfoStore(TaskClassValidatorExtractor validatorExtractor) {
        this.validatorExtractor = validatorExtractor;
    }

    @Override
    public TaskClassInfo getTaskClassInfo(Class<? extends Task> type) {
        return classInfos.getUnchecked(type);
    }

    private TaskClassInfo createTaskClassInfo(Class<? extends Task> type) {
        boolean incremental = false;
        Map<String, Class<?>> processedMethods = Maps.newHashMap();
        ImmutableList.Builder<Action<? super Task>> taskActionsBuilder = ImmutableList.builder();
        for (Class current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Action<? super Task> taskAction = createTaskAction(type, method, processedMethods);
                if (taskAction == null) {
                    continue;
                }
                if (taskAction instanceof IncrementalTaskAction) {
                    if (incremental) {
                        throw new GradleException(String.format("Cannot have multiple @TaskAction methods accepting an %s parameter.", IncrementalTaskInputs.class.getSimpleName()));
                    }
                    incremental = true;
                }
                taskActionsBuilder.add(taskAction);
            }
        }

        TaskClassValidator validator = validatorExtractor.extractValidator(type);

        return new TaskClassInfo(incremental, taskActionsBuilder.build(), validator);
    }

    @Nullable
    private static Action<? super Task> createTaskAction(Class<? extends Task> taskType, final Method method, Map<String, Class<?>> processedMethods) {
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

        Action<? super Task> taskAction;
        if (parameterTypes.length == 1) {
            if (!parameterTypes[0].equals(IncrementalTaskInputs.class)) {
                throw new GradleException(String.format(
                    "Cannot use @TaskAction annotation on method %s.%s() because %s is not a valid parameter to an action method.",
                    declaringClass.getSimpleName(), method.getName(), parameterTypes[0]));
            }
            taskAction = new IncrementalTaskAction(taskType, method);
        } else {
            taskAction = new StandardTaskAction(taskType, method);
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
        return taskAction;
    }
}
