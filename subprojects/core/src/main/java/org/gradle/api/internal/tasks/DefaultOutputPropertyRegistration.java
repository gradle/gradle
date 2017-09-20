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
import org.gradle.api.tasks.OutputPropertyRegistration;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;

@NonNullApi
public class DefaultOutputPropertyRegistration implements OutputPropertyRegistration{
    private final TaskMutator taskMutator;
    private final String taskName;
    private final FileResolver resolver;

    private final List<TaskOutputPropertySpecAndBuilder> fileProperties = Lists.newArrayList();

    public DefaultOutputPropertyRegistration(String taskName, TaskMutator taskMutator, FileResolver resolver) {
        this.taskMutator = taskMutator;
        this.taskName = taskName;
        this.resolver = resolver;
    }

    @Override
    public TaskOutputFilePropertyBuilder file(final Object path) {
        return taskMutator.mutate("TaskOutputs.file(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new DefaultCacheableTaskOutputFilePropertySpec(taskName, resolver, OutputType.FILE, path));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(final Object path) {
        return taskMutator.mutate("TaskOutputs.dir(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new DefaultCacheableTaskOutputFilePropertySpec(taskName, resolver, OutputType.DIRECTORY, path));
            }
        });
    }

    @Override
    public boolean getHasOutput() {
        return !fileProperties.isEmpty();
    }

    @Override
    public TaskOutputFilePropertyBuilder files(@Nullable final Object... paths) {
        return taskMutator.mutate("TaskOutputs.files(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new CompositeTaskOutputPropertySpec(taskName, resolver, OutputType.FILE, paths));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dirs(@Nullable final Object... paths) {
        return taskMutator.mutate("TaskOutputs.dirs(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new CompositeTaskOutputPropertySpec(taskName, resolver, OutputType.DIRECTORY, paths));
            }
        });
    }

    private TaskOutputFilePropertyBuilder addSpec(TaskOutputPropertySpecAndBuilder spec) {
        fileProperties.add(spec);
        return spec;
    }

    public List<TaskOutputPropertySpecAndBuilder> getFileProperties() {
        return fileProperties;
    }
}
