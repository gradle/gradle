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
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.properties.annotations.FunctionMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.work.InputChanges;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@NonNullApi
public class DefaultTaskClassInfoStore implements TaskClassInfoStore {
    private final CrossBuildInMemoryCache<Class<?>, TaskClassInfo> classInfos;
    private final TypeMetadataStore typeMetadataStore;

    public DefaultTaskClassInfoStore(CrossBuildInMemoryCacheFactory cacheFactory, TypeMetadataStore typeMetadataStore) {
        this.classInfos = cacheFactory.newClassCache();
        this.typeMetadataStore = typeMetadataStore;
    }

    @Override
    public TaskClassInfo getTaskClassInfo(Class<? extends Task> type) {
        return classInfos.get(type, aClass -> createTaskClassInfo(aClass.asSubclass(Task.class)));
    }

    private TaskClassInfo createTaskClassInfo(Class<? extends Task> type) {
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(type);
        boolean cacheable = typeMetadata.getTypeAnnotationMetadata().isAnnotationPresent(CacheableTask.class);
        Optional<String> reasonNotToTrackState = typeMetadata.getTypeAnnotationMetadata().getAnnotation(UntrackedTask.class)
            .map(UntrackedTask::because);

        Map<String, FunctionMetadata> functions = new HashMap<>();

        ImmutableList.Builder<TaskActionFactory> taskActionFactoriesBuilder = ImmutableList.builder();
        typeMetadata.getFunctionMetadata().stream().filter(functionMetadata -> functionMetadata.getAnnotation(TaskAction.class).isPresent()).forEach(functionMetadata -> {
            FunctionMetadata alreadySeen = functions.put(functionMetadata.getMethodName(), functionMetadata);
            if (alreadySeen != null) {
                throw new GradleException(String.format(
                    "Cannot use @TaskAction annotation on multiple overloads of method %s.%s()",
                    typeMetadata.getType().getSimpleName(), functionMetadata.getMethod().getName()
                ));
            }

            // TODO These validations should be done as validation in TaskActionAnnotationHandler and surfaced as problems
            Class<?> declaringClass = functionMetadata.getMethod().getDeclaringClass();
            final Class<?>[] parameterTypes = functionMetadata.getMethod().getParameterTypes();
            if (parameterTypes.length > 1) {
                throw new GradleException(String.format(
                    "Cannot use @TaskAction annotation on method %s.%s() as this method takes multiple parameters.",
                    declaringClass.getSimpleName(), functionMetadata.getMethodName()));
            }

            TaskActionFactory taskActionFactory;
            if (functionMetadata.getMethod().getParameterTypes().length == 1) {
                Class<?> parameterType = parameterTypes[0];
                if (!parameterType.equals(InputChanges.class)) {
                    throw new GradleException(String.format(
                        "Cannot use @TaskAction annotation on method %s.%s() because %s is not a valid parameter to an action method.",
                        declaringClass.getSimpleName(), functionMetadata.getMethodName(), parameterType));
                }
                taskActionFactory = new IncrementalTaskActionFactory(type, functionMetadata.getMethod());
            } else {
                taskActionFactory = new StandardTaskActionFactory(type, functionMetadata.getMethod());
            }
            taskActionFactoriesBuilder.add(taskActionFactory);
        });

        if (functions.values().stream().filter(functionMetadata -> functionMetadata.getMethod().getParameterTypes().length > 0).count() > 1) {
            throw new GradleException(String.format("Cannot have multiple @TaskAction methods accepting an %s parameter.", InputChanges.class.getSimpleName()));
        }

        return new TaskClassInfo(taskActionFactoriesBuilder.build(), cacheable, reasonNotToTrackState, typeMetadata);
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

    private static class IncrementalTaskActionFactory implements TaskActionFactory {
        private final Class<? extends Task> taskType;
        private final Method method;

        public IncrementalTaskActionFactory(Class<? extends Task> taskType, Method method) {
            this.taskType = taskType;
            this.method = method;
        }

        public Method getMethod() {
            return method;
        }

        @Override
        public Action<? super Task> create(Instantiator instantiator) {
            return new IncrementalTaskAction(taskType, method);
        }
    }
}
