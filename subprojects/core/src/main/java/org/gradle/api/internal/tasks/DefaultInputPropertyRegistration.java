/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.InputPropertyRegistration;
import org.gradle.api.tasks.TaskInputs;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@NonNullApi
public class DefaultInputPropertyRegistration implements InputPropertyRegistration {
    private final String taskName;
    private final TaskMutator taskMutator;
    private final FileResolver resolver;

    private List<TaskInputPropertySpecAndBuilder> fileProperties = Lists.newArrayList();
    private final Map<String, Object> properties = new HashMap<String, Object>();

    public DefaultInputPropertyRegistration(String taskName, TaskMutator taskMutator, FileResolver resolver) {
        this.taskName = taskName;
        this.taskMutator = taskMutator;
        this.resolver = resolver;
    }

    @Override
    public boolean getHasInputs() {
        return !properties.isEmpty() || !fileProperties.isEmpty();
    }

    @Override
    public TaskInputFilePropertyBuilderInternal files(final Object... paths) {
        return taskMutator.mutate("TaskInputs.files(Object...)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return addSpec(paths);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal file(final Object path) {
        return taskMutator.mutate("TaskInputs.file(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return addSpec(path);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal dir(final Object dirPath) {
        return taskMutator.mutate("TaskInputs.dir(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return addSpec(resolver.resolveFilesAsTree(dirPath));
            }
        });
    }

    private TaskInputFilePropertyBuilderInternal addSpec(Object paths) {
        DefaultTaskInputPropertySpec spec = new DefaultTaskInputPropertySpec(taskName, resolver, paths);
        fileProperties.add(spec);
        return spec;
    }

    @Nullable
    public TaskInputs property(final String name, @Nullable final Object value) {
        taskMutator.mutate("TaskInputs.property(String, Object)", new Runnable() {
            public void run() {
                properties.put(name, value);
            }
        });
        return null;
    }

    @Nullable
    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            public void run() {
                properties.putAll(newProps);
            }
        });
        return null;
    }


    public List<TaskInputPropertySpecAndBuilder> getFileProperties() {
        return fileProperties;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
