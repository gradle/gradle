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
package org.gradle.api.internal.tasks;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import groovy.lang.GString;
import org.gradle.api.Describable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.TaskPropertyUtils.ensurePropertiesHaveNames;
import static org.gradle.util.GUtil.uncheckedCall;

@NonNullApi
public class DefaultTaskInputs implements TaskInputsInternal {
    private final FileCollection allInputFiles;
    private final FileCollection allSourceFiles;
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final Map<String, PropertyValue> properties = new HashMap<String, PropertyValue>();
    private final List<TaskInputPropertySpecAndBuilder> filePropertiesInternal = Lists.newArrayList();
    private ImmutableSortedSet<TaskInputFilePropertySpec> fileProperties;

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
        String taskName = task.getName();
        this.allInputFiles = new TaskInputUnionFileCollection(taskName, "input", false, filePropertiesInternal);
        this.allSourceFiles = new TaskInputUnionFileCollection(taskName, "source", true, filePropertiesInternal);
    }

    @Override
    public boolean getHasInputs() {
        return !filePropertiesInternal.isEmpty() || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            ensurePropertiesHaveNames(filePropertiesInternal);
            fileProperties = TaskPropertyUtils.<TaskInputFilePropertySpec>collectFileProperties("input", filePropertiesInternal.iterator());
        }
        return fileProperties;
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

    @Override
    public boolean getHasSourceFiles() {
        for (TaskInputPropertySpecAndBuilder propertySpec : filePropertiesInternal) {
            if (propertySpec.isSkipWhenEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FileCollection getSourceFiles() {
        return allSourceFiles;
    }

    private TaskInputFilePropertyBuilderInternal addSpec(Object paths) {
        DefaultTaskInputFilePropertySpec spec = new DefaultTaskInputFilePropertySpec(task.getName(), resolver, paths);
        filePropertiesInternal.add(spec);
        return spec;
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            try {
                Object value = prepareValue(entry.getValue().getValue());
                actualProperties.put(propertyName, value);
            } catch (Exception ex) {
                throw new InvalidUserDataException(String.format("Error while evaluating property '%s' of %s", propertyName, task), ex);
            }
        }
        return actualProperties;
    }

    @Nullable
    private Object prepareValue(@Nullable Object value) {
        while (true) {
            if (value instanceof Callable) {
                Callable callable = (Callable) value;
                value = uncheckedCall(callable);
            } else if (value instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) value;
                return fileCollection.getFiles();
            } else {
                return avoidGString(value);
            }
        }
    }

    @Nullable
    private static Object avoidGString(@Nullable Object value) {
        return (value instanceof GString) ? value.toString() : value;
    }

    public TaskInputPropertyBuilder property(final String name, @Nullable final Object value) {
        return taskMutator.mutate("TaskInputs.property(String, Object)", new Callable<TaskInputPropertyBuilder>() {
            @Override
            public TaskInputPropertyBuilder call() throws Exception {
                return setProperty(name, value);
            }
        });
    }

    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, ?> entry : newProps.entrySet()) {
                    setProperty(entry.getKey(), entry.getValue());
                }
            }
        });
        return this;
    }

    private TaskInputPropertyBuilder setProperty(String name, Object value) {
        PropertyValue propertyValue = properties.get(name);
        DeclaredTaskInputProperty spec;
        if (propertyValue == null) {
            spec = new DefaultTaskInputPropertySpec(name);
            propertyValue = new PropertyValue(spec, value);
            properties.put(name, propertyValue);
        } else {
            spec = propertyValue.getPropertySpec();
            propertyValue.setValue(value);
        }
        return spec;
    }

    private static class PropertyValue {
        private final DeclaredTaskInputProperty propertySpec;
        private Object value;

        public PropertyValue(DeclaredTaskInputProperty propertySpec, @Nullable Object value) {
            this.propertySpec = propertySpec;
            this.value = value;
        }

        public DeclaredTaskInputProperty getPropertySpec() {
            return propertySpec;
        }

        @Nullable
        public Object getValue() {
            return value;
        }

        public void setValue(@Nullable Object value) {
            this.value = value;
        }
    }

    private static class TaskInputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final boolean skipWhenEmptyOnly;
        private final String taskName;
        private final String type;
        private final List<TaskInputPropertySpecAndBuilder> filePropertiesInternal;

        public TaskInputUnionFileCollection(String taskName, String type, boolean skipWhenEmptyOnly, List<TaskInputPropertySpecAndBuilder> filePropertiesInternal) {
            this.taskName = taskName;
            this.type = type;
            this.skipWhenEmptyOnly = skipWhenEmptyOnly;
            this.filePropertiesInternal = filePropertiesInternal;
        }

        @Override
        public String getDisplayName() {
            return "task '" + taskName + "' " + type + " files";
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            for (TaskInputPropertySpecAndBuilder fileProperty : filePropertiesInternal) {
                if (!skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                    context.add(fileProperty.getPropertyFiles());
                }
            }
        }
    }
}
