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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import groovy.lang.GString;
import org.gradle.api.Describable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ChangeDetection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.TaskInputs;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.TaskPropertyUtils.ensurePropertiesHaveNames;
import static org.gradle.util.GUtil.uncheckedCall;

@NonNullApi
public class DefaultTaskInputs implements TaskInputsInternal {
    private final FileCollection allInputFiles;
    private final FileCollection allSourceFiles;
    private final TaskInternal task;
    private final ChangeDetection changeDetection;
    private final DefaultInputPropertyRegistration userSuppliedProperties;
    private DefaultInputPropertyRegistration discoveredProperties;

    private final Supplier<Iterable<TaskInputPropertySpecAndBuilder>> filePropertiesInternalSupplier = new Supplier<Iterable<TaskInputPropertySpecAndBuilder>>() {

        @Override
        public Iterable<TaskInputPropertySpecAndBuilder> get() {
            changeDetection.ensureTaskInputsAndOutputsDiscovered();
            return getFilePropertiesInternal();
        }
    };
    private ImmutableSortedSet<TaskInputFilePropertySpec> fileProperties;
    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator, ChangeDetection changeDetection) {
        this.task = task;
        this.changeDetection = changeDetection;
        String taskName = task.getName();
        this.allInputFiles = new TaskInputUnionFileCollection(taskName, "input", false, filePropertiesInternalSupplier);
        this.allSourceFiles = new TaskInputUnionFileCollection(taskName, "source", true, filePropertiesInternalSupplier);
        this.userSuppliedProperties = new DefaultInputPropertyRegistration(taskName, taskMutator, resolver);
    }

    @Override
    public boolean getHasInputs() {
        changeDetection.ensureTaskInputsAndOutputsDiscovered();
        return userSuppliedProperties.getHasInputs() || discoveredProperties.getHasInputs();
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties() {
        changeDetection.ensureTaskInputsAndOutputsDiscovered();
        if (fileProperties == null) {
            Iterable<TaskInputPropertySpecAndBuilder> allSpecs = getFilePropertiesInternal();
            ensurePropertiesHaveNames(allSpecs);
            fileProperties = TaskPropertyUtils.<TaskInputFilePropertySpec>collectFileProperties("input", allSpecs.iterator());
        }
        return fileProperties;
    }

    private Iterable<TaskInputPropertySpecAndBuilder> getFilePropertiesInternal() {
        return Iterables.concat(userSuppliedProperties.getFileProperties(), discoveredProperties.getFileProperties());
    }

    @Override
    public TaskInputFilePropertyBuilderInternal files(Object... paths) {
        return userSuppliedProperties.files(paths);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal file(Object path) {
        return userSuppliedProperties.file(path);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal dir(Object dirPath) {
        return userSuppliedProperties.dir(dirPath);
    }

    @Override
    @Nullable
    public TaskInputs property(String name, @Nullable Object value) {
        return userSuppliedProperties.property(name, value);
    }

    @Override
    @Nullable
    public TaskInputs properties(Map<String, ?> newProps) {
        return userSuppliedProperties.properties(newProps);
    }

    @Override
    public boolean getHasSourceFiles() {
        changeDetection.ensureTaskInputsAndOutputsDiscovered();
        for (TaskInputPropertySpecAndBuilder propertySpec : getFilePropertiesInternal()) {
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

    public Map<String, Object> getProperties() {
        changeDetection.ensureTaskInputsAndOutputsDiscovered();
        Map<String, Object> result = Maps.newHashMap();
        addResolvedProperties(result, userSuppliedProperties.getProperties(), ImmutableSortedSet.<String>of());
        addResolvedProperties(result, discoveredProperties.getProperties(), userSuppliedProperties.getProperties().keySet());
        return result;
    }

    private void addResolvedProperties(Map<String, Object> builder, Map<String, Object> properties, Set<String> registeredProperties) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            if (registeredProperties.contains(propertyName)) {
                continue;
            }
            try {
                Object value = prepareValue(entry.getValue());
                builder.put(propertyName, value);
            } catch (Exception ex) {
                throw new InvalidUserDataException(String.format("Error while evaluating property '%s' of %s", propertyName, task), ex);
            }
        }
    }

    private Object prepareValue(Object value) {
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

    private static Object avoidGString(Object value) {
        return (value instanceof GString) ? value.toString() : value;
    }

    public void setDiscoveredProperties(DefaultInputPropertyRegistration discoveredProperties) {
        this.discoveredProperties = discoveredProperties;
        this.fileProperties = null;
    }

    private static class TaskInputUnionFileCollection extends CompositeFileCollection implements Describable {

        private final boolean skipWhenEmptyOnly;
        private final String taskName;
        private final String type;
        private final Supplier<Iterable<TaskInputPropertySpecAndBuilder>> filePropertiesInternal;

        public TaskInputUnionFileCollection(String taskName, String type, boolean skipWhenEmptyOnly, Supplier<Iterable<TaskInputPropertySpecAndBuilder>> filePropertiesInternal) {
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
            for (TaskInputPropertySpecAndBuilder fileProperty : filePropertiesInternal.get()) {
                if (!skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                    context.add(fileProperty.getPropertyFiles());
                }
            }
        }
    }
}
