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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;
import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class DefaultTaskOutputs extends FilePropertyContainer implements TaskOutputsInternal {
    private static final AndSpec<TaskInternal> EMPTY_AND_SPEC = new AndSpec<TaskInternal>();

    private final FileCollection allOutputFiles;
    private AndSpec<TaskInternal> upToDateSpec = EMPTY_AND_SPEC;
    private TaskExecutionHistory history;
    private final List<BasePropertySpec> filePropertiesInternal = Lists.newArrayList();
    private SortedSet<TaskFilePropertySpec> fileProperties;
    private final FileResolver resolver;
    private final String taskName;
    private final TaskMutator taskMutator;

    public DefaultTaskOutputs(FileResolver resolver, final TaskInternal task, TaskMutator taskMutator) {
        super("output");
        this.resolver = resolver;
        this.taskName = task.getName();
        this.taskMutator = taskMutator;

        final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
        buildDependencies.add(task);
        this.allOutputFiles = new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return task + " output files";
            }

            @Override
            public void visitContents(FileCollectionResolveContext context) {
                for (TaskFilePropertySpec propertySpec : getFileProperties()) {
                    context.add(propertySpec.getPropertyFiles());
                }
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(buildDependencies);
                super.visitDependencies(context);
            }
        };
    }

    @Override
    public Spec<? super TaskInternal> getUpToDateSpec() {
        return upToDateSpec;
    }

    @Override
    public void upToDateWhen(final Closure upToDateClosure) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Closure)", new Runnable() {
            public void run() {
                upToDateSpec = upToDateSpec.and(upToDateClosure);
            }
        });
    }

    @Override
    public void upToDateWhen(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Spec)", new Runnable() {
            public void run() {
                upToDateSpec = upToDateSpec.and(spec);
            }
        });
    }

    @Override
    public boolean getHasOutput() {
        return !filePropertiesInternal.isEmpty() || !upToDateSpec.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allOutputFiles;
    }

    public SortedSet<TaskFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            ensurePropertiesHaveNames(filePropertiesInternal);
            final Iterator<BasePropertySpec> rootIterator = filePropertiesInternal.iterator();
            fileProperties = collectFileProperties(new AbstractIterator<TaskFilePropertySpec>() {
                private Iterator<TaskFilePropertySpec> childIterator;

                @Override
                protected TaskFilePropertySpec computeNext() {
                    while (true) {
                        if (childIterator != null) {
                            if (childIterator.hasNext()) {
                                return childIterator.next();
                            }
                            childIterator = null;
                        }
                        if (!rootIterator.hasNext()) {
                            return endOfData();
                        }
                        BasePropertySpec propertySpec = rootIterator.next();
                        if (propertySpec instanceof CompositePropertySpec) {
                            childIterator = ((CompositePropertySpec) propertySpec).iterator();
                        } else {
                            return (TaskFilePropertySpec) propertySpec;
                        }
                    }
                }
            });
        }
        return fileProperties;
    }

    @Override
    public TaskOutputFilePropertyBuilder file(final Object path) {
        return taskMutator.mutate("TaskOutputs.file(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSimpleSpec(path);
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(final Object path) {
        return taskMutator.mutate("TaskOutputs.dir(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSimpleSpec(path);
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder namedFiles(final Callable<Map<?, ?>> paths) {
        return taskMutator.mutate("TaskOutputs.namedFiles(Callable)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new NamedCompositePropertySpec(resolver, paths));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder namedFiles(final Map<?, ?> paths) {
        return taskMutator.mutate("TaskOutputs.namedFiles(Map)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                Callable<Map<?, ?>> callable = Callables.<Map<?, ?>>returning(ImmutableMap.copyOf(paths));
                return addSpec(new NamedCompositePropertySpec(resolver, callable));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder files(final Object... paths) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskOutputs.files(Object...)", "Please use the TaskOutputs.file(Object) or the TaskOutputs.dir(Object) method instead.");
        return taskMutator.mutate("TaskOutputs.files(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new UnnamedCompositePropertySpec(taskName, resolver, paths));
            }
        });
    }

    private TaskOutputFilePropertyBuilder addSimpleSpec(Object paths) {
        return addSpec(new DefaultPropertySpec(taskName, resolver, paths));
    }

    private TaskOutputFilePropertyBuilder addSpec(BasePropertySpec spec) {
        filePropertiesInternal.add(spec);
        return spec;
    }

    @Override
    public FileCollection getPreviousFiles() {
        if (history == null) {
            throw new IllegalStateException("Task history is currently not available for this task.");
        }
        return history.getOutputFiles();
    }

    @Override
    public void setHistory(TaskExecutionHistory history) {
        this.history = history;
    }

    private interface CompositePropertySpec extends TaskPropertySpec, Iterable<TaskFilePropertySpec> {
    }

    abstract private class BasePropertySpec extends AbstractTaskPropertyBuilder implements TaskPropertySpec, TaskOutputFilePropertyBuilder {
        private boolean optional;

        @Override
        public TaskOutputFilePropertyBuilder withPropertyName(String propertyName) {
            setPropertyName(propertyName);
            return this;
        }

        public boolean isOptional() {
            return optional;
        }

        @Override
        public TaskOutputFilePropertyBuilder optional() {
            return optional(true);
        }

        @Override
        public TaskOutputFilePropertyBuilder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        // --- Deprecated delegate methods

        private TaskOutputs getTaskOutputs(String method) {
            DeprecationLogger.nagUserOfDiscontinuedMethod("chaining of the " + method, String.format("Use '%s' on TaskOutputs directly instead.", method));
            return DefaultTaskOutputs.this;
        }

        @Override
        public void upToDateWhen(Closure upToDateClosure) {
            getTaskOutputs("upToDateWhen(Closure)").upToDateWhen(upToDateClosure);
        }

        @Override
        public void upToDateWhen(Spec<? super Task> upToDateSpec) {
            getTaskOutputs("upToDateWhen(Spec)").upToDateWhen(upToDateSpec);
        }

        @Override
        public boolean getHasOutput() {
            return getTaskOutputs("getHasOutput()").getHasOutput();
        }

        @Override
        public FileCollection getFiles() {
            return getTaskOutputs("getFiles()").getFiles();
        }

        @Override
        @Deprecated
        public TaskOutputs files(Object... paths) {
            return getTaskOutputs("files(Object...)").files(paths);
        }

        @Override
        @Deprecated
        public TaskOutputFilePropertyBuilder namedFiles(Callable<Map<?, ?>> paths) {
            return getTaskOutputs("namedFiles(Callable)").namedFiles(paths);
        }

        @Override
        @Deprecated
        public TaskOutputFilePropertyBuilder namedFiles(Map<?, ?> paths) {
            return getTaskOutputs("namedFiles(Map)").namedFiles(paths);
        }

        @Override
        public TaskOutputFilePropertyBuilder file(Object path) {
            return getTaskOutputs("file(Object)").file(path);
        }

        @Override
        public TaskOutputFilePropertyBuilder dir(Object path) {
            return getTaskOutputs("dir(Object)").dir(path);
        }

        @Override
        public int compareTo(TaskPropertySpec o) {
            return getPropertyName().compareTo(o.getPropertyName());
        }
    }

    private class DefaultPropertySpec extends BasePropertySpec implements TaskFilePropertySpec {
        private final TaskPropertyFileCollection files;

        public DefaultPropertySpec(String taskName, FileResolver resolver, Object path) {
            this.files = new TaskPropertyFileCollection(taskName, "output", this, resolver, path);
        }

        @Override
        public FileCollection getPropertyFiles() {
            return files;
        }
    }

    private class UnnamedCompositePropertySpec extends DefaultPropertySpec implements CompositePropertySpec {

        public UnnamedCompositePropertySpec(String taskName, FileResolver resolver, Object paths) {
            super(taskName, resolver, paths);
        }

        @Override
        public Iterator<TaskFilePropertySpec> iterator() {
            final Iterator<File> fileIterator = getPropertyFiles().iterator();
            return new AbstractIterator<TaskFilePropertySpec>() {
                int counter;

                @Override
                protected TaskFilePropertySpec computeNext() {
                    if (fileIterator.hasNext()) {
                        return new ElementPropertySpec(UnnamedCompositePropertySpec.this, "$" + (++counter), fileIterator.next());
                    }
                    return endOfData();
                }
            };
        }
    }

    private class NamedCompositePropertySpec extends BasePropertySpec implements CompositePropertySpec {

        private final Callable<Map<?, ?>> paths;
        private final FileResolver resolver;

        public NamedCompositePropertySpec(FileResolver resolver, Callable<Map<?, ?>> paths) {
            this.resolver = resolver;
            this.paths = paths;
        }

        @Override
        public Iterator<TaskFilePropertySpec> iterator() {
            final Iterator<? extends Map.Entry<?, ?>> iterator = uncheckedCall(paths).entrySet().iterator();
            return new AbstractIterator<TaskFilePropertySpec>() {
                @Override
                protected TaskFilePropertySpec computeNext() {
                    if (iterator.hasNext()) {
                        Map.Entry<?, ?> entry = iterator.next();
                        String id = entry.getKey().toString();
                        File file = resolver.resolve(entry.getValue());
                        return new ElementPropertySpec(NamedCompositePropertySpec.this, "." + id, file);
                    }
                    return endOfData();
                }
            };
        }
    }

    private class ElementPropertySpec implements TaskFilePropertySpec {
        private final CompositePropertySpec parentProperty;
        private final String propertySuffix;
        private final FileCollection files;

        public ElementPropertySpec(CompositePropertySpec parentProperty, String propertySuffix, File file) {
            this.parentProperty = parentProperty;
            this.propertySuffix = propertySuffix;
            this.files = new SimpleFileCollection(Collections.singleton(file));
        }

        @Override
        public String getPropertyName() {
            return parentProperty.getPropertyName() + propertySuffix;
        }

        @Override
        public FileCollection getPropertyFiles() {
            return files;
        }

        @Override
        public int compareTo(TaskPropertySpec o) {
            return getPropertyName().compareTo(o.getPropertyName());
        }
    }
}
