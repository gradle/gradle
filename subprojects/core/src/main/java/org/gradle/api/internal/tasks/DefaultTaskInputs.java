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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import groovy.lang.GString;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.tasks.TaskFileInputPropertySpec;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class DefaultTaskInputs implements TaskInputsInternal {
    private final FileResolver resolver;
    private final String taskName;
    private final TaskMutator taskMutator;
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final List<DefaultTaskFilePropertyInputSpec> fileProperties = Lists.newArrayList();
    private int legacyFilePropertyCounter;
    private Queue<Action<? super TaskInputs>> configureActions;

    public DefaultTaskInputs(FileResolver resolver, String taskName, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.taskName = taskName;
        this.taskMutator = taskMutator;
    }

    @Override
    public boolean getHasInputs() {
        return !fileProperties.isEmpty() || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        UnionFileCollection files = new UnionFileCollection();
        for (DefaultTaskFilePropertyInputSpec propertySpec : fileProperties) {
            files.add(propertySpec.getFiles());
        }
        return files;
    }

    @Override
    public Collection<TaskFileInputPropertySpecInternal> getFileProperties() {
        ImmutableList.Builder<TaskFileInputPropertySpecInternal> builder = ImmutableList.builder();
        for (DefaultTaskFilePropertyInputSpec propertySpec : fileProperties) {
            if (propertySpec.getPropertyName() == null) {
                propertySpec.withPropertyName(nextLegacyPropertyName());
            }
            builder.add(propertySpec);
        }
        return builder.build();
    }

    @Override
    public TaskFileInputPropertySpec includeFile(Object path) {
        return include("includeFile(Object)", new DefaultTaskFilePropertyInputSpec(taskName, resolver), new AddFilesAction(path));
    }

    @Override
    public TaskFileInputPropertySpec includeDir(Object path) {
        return include("includeDir(Object)", new DefaultTaskFilePropertyInputSpec(taskName, resolver), new AddDirAction(path));
    }

    @Override
    public TaskFileInputPropertySpec includeFiles(Object... paths) {
        return include("includeFiles(Object...)", new DefaultTaskFilePropertyInputSpec(taskName, resolver), new AddFilesAction(paths));
    }

    @Override
    public TaskInputs files(Object... paths) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.files(Object...)", "TaskInputs.includeFiles(Object...)");
        include("files(Object...)", new DefaultTaskFilePropertyInputSpec(taskName, resolver), new AddFilesAction(paths));
        return this;
    }

    @Override
    public TaskInputs file(Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.file(Object)", "TaskInputs.includeFile(Object)");
        include("file(Object)", new DefaultTaskFilePropertyInputSpec(taskName, resolver), new AddFilesAction(path));
        return this;
    }

    @Override
    public TaskInputs dir(Object dirPath) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.dir(Object)", "TaskInputs.includeDir(Object)");
        include("dir(Object)", new DefaultTaskFilePropertyInputSpec(taskName, resolver), new AddDirAction(dirPath));
        return this;
    }

    @Override
    public boolean getHasSourceFiles() {
        for (DefaultTaskFilePropertyInputSpec propertySpec : fileProperties) {
            if (propertySpec.isSkipWhenEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FileCollection getSourceFiles() {
        UnionFileCollection files = new UnionFileCollection();
        for (DefaultTaskFilePropertyInputSpec propertySpec : fileProperties) {
            if (propertySpec.isSkipWhenEmpty()) {
                files.add(propertySpec.getFiles());
            }
        }
        return files;
    }

    @Override
    public TaskInputs source(Object... paths) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.source(Object...)", "TaskInputs.includeFiles(Object...)");
        include("source(Object...)", new DefaultTaskFilePropertyInputSpec(taskName, true, resolver),
            new AddFilesAction(paths));
        return this;
    }

    @Override
    public TaskInputs source(Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.source(Object)", "TaskInputs.includeFile(Object)");
        include("source(Object)", new DefaultTaskFilePropertyInputSpec(taskName, true, resolver),
            new AddFilesAction(path));
        return this;
    }

    @Override
    public TaskInputs sourceDir(Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.sourceDir(Object)", "TaskInputs.includeDir(Object)");
        include("sourceDir(Object)", new DefaultTaskFilePropertyInputSpec(taskName, true, resolver),
            new AddDirAction(path));
        return this;
    }

    private TaskFileInputPropertySpec include(String method, final DefaultTaskFilePropertyInputSpec spec, final Action<? super TaskPropertyFileCollection> addPathsAction) {
        taskMutator.mutate("TaskInputs." + method, new Runnable() {
            @Override
            public void run() {
                addPathsAction.execute(spec.getFiles());
                fileProperties.add(spec);
            }
        });
        return spec;
    }

    private String nextLegacyPropertyName() {
        return "$" + (++legacyFilePropertyCounter);
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = prepareValue(entry.getValue());
            actualProperties.put(entry.getKey(), value);
        }
        return actualProperties;
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

    public TaskInputs property(final String name, final Object value) {
        taskMutator.mutate("TaskInputs.property(String, Object)", new Runnable() {
            public void run() {
                properties.put(name, value);
            }
        });
        return this;
    }

    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            public void run() {
                properties.putAll(newProps);
            }
        });
        return this;
    }

    @Override
    public TaskInputs configure(final Action<? super TaskInputs> action) {
        taskMutator.mutate("TaskInputs.configure(Action)", new Runnable() {
            public void run() {
                if (configureActions == null) {
                    configureActions = Lists.newLinkedList();
                }
                configureActions.add(action);
            }
        });
        return this;
    }

    @Override
    public TaskInputs configure(Closure action) {
        return configure(ConfigureUtil.configureUsing(action));
    }

    @Override
    public void ensureConfigured() {
        if (configureActions != null) {
            while (!configureActions.isEmpty()) {
                configureActions.remove().execute(this);
            }
            configureActions = null;
        }
    }

    private static class DefaultTaskFilePropertyInputSpec implements TaskFileInputPropertySpecInternal {

        private final TaskPropertyFileCollection files;
        private String propertyName;
        private boolean skipWhenEmpty;
        private boolean optional;

        public DefaultTaskFilePropertyInputSpec(String taskName, FileResolver resolver) {
            this(taskName, false, resolver);
        }

        public DefaultTaskFilePropertyInputSpec(String taskName, boolean skipWhenEmpty, FileResolver resolver) {
            this.files = new TaskPropertyFileCollection(taskName, "input", this, resolver);
            this.skipWhenEmpty = skipWhenEmpty;
        }

        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public TaskFileInputPropertySpec withPropertyName(String propertyName) {
            this.propertyName = propertyName;
            return this;
        }

        @Override
        public TaskPropertyFileCollection getFiles() {
            return files;
        }

        @Override
        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }

        @Override
        public TaskFileInputPropertySpecInternal skipWhenEmpty(boolean skipWhenEmpty) {
            this.skipWhenEmpty = skipWhenEmpty;
            return this;
        }

        @Override
        public TaskFileInputPropertySpec skipWhenEmpty() {
            return skipWhenEmpty(true);
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public TaskFileInputPropertySpecInternal optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        @Override
        public TaskFileInputPropertySpec optional() {
            return optional(true);
        }
    }

    private static class AddFilesAction implements Action<TaskPropertyFileCollection> {
        private final Object paths;

        public AddFilesAction(Object paths) {
            this.paths = paths;
        }

        @Override
        public void execute(TaskPropertyFileCollection files) {
            files.from(paths);
        }
    }

    private class AddDirAction implements Action<TaskPropertyFileCollection> {
        private final Object path;

        public AddDirAction(Object path) {
            this.path = path;
        }

        @Override
        public void execute(TaskPropertyFileCollection files) {
            files.from(resolver.resolveFilesAsTree(path));
        }
    }
}
