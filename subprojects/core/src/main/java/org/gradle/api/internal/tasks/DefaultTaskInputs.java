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
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;
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
    private final List<DeclaredTaskInputFileProperty> declaredFileProperties = Lists.newArrayList();
    private final TaskInputs deprecatedThis;
    private ImmutableSortedSet<TaskInputFilePropertySpec> fileProperties;

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
        String taskName = task.getName();
        this.allInputFiles = new TaskInputUnionFileCollection(taskName, "input", false, declaredFileProperties);
        this.allSourceFiles = new TaskInputUnionFileCollection(taskName, "source", true, declaredFileProperties);
        this.deprecatedThis = new LenientTaskInputsDeprecationSupport(this);
    }

    @Override
    public boolean getHasInputs() {
        return !declaredFileProperties.isEmpty() || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            ensurePropertiesHaveNames(declaredFileProperties);
            fileProperties = TaskPropertyUtils.<TaskInputFilePropertySpec>collectFileProperties("input", declaredFileProperties.iterator());
        }
        return fileProperties;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal files(final Object... paths) {
        return taskMutator.mutate("TaskInputs.files(Object...)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return registerFiles(new StaticValue(unpackVarargs(paths)));
            }
        });
    }

    private static Object unpackVarargs(Object[] args) {
        if (args.length == 1) {
            return args[0];
        }
        return args;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerFiles(ValidatingValue paths) {
        return addSpec(paths, ValidationAction.NO_OP);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal file(final Object path) {
        return taskMutator.mutate("TaskInputs.file(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return fileInternal(new StaticValue(path), RUNTIME_INPUT_FILE_VALIDATOR);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerFile(ValidatingValue value) {
        return fileInternal(value, INPUT_FILE_VALIDATOR);
    }

    private TaskInputFilePropertyBuilderInternal fileInternal(ValidatingValue value, ValidationAction validator) {
        return addSpec(value, validator);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal dir(final Object dirPath) {
        return taskMutator.mutate("TaskInputs.dir(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return dir(new StaticValue(dirPath), RUNTIME_INPUT_DIRECTORY_VALIDATOR);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerDir(final ValidatingValue dirPath) {
        return dir(dirPath, INPUT_DIRECTORY_VALIDATOR);
    }

    private TaskInputFilePropertyBuilderInternal dir(ValidatingValue dirPath, ValidationAction validator) {
        FileTreeInternal fileTree = resolver.resolveFilesAsTree(dirPath);
        return addSpec(new FileTreeValue(dirPath, fileTree), validator);
    }

    @Override
    public boolean getHasSourceFiles() {
        for (DeclaredTaskInputFileProperty propertySpec : declaredFileProperties) {
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

    @Override
    public void validate(TaskValidationContext context) {
        TaskPropertyUtils.ensurePropertiesHaveNames(declaredFileProperties);
        for (PropertyValue propertyValue : properties.values()) {
            propertyValue.getPropertySpec().validate(context);
        }
        for (DeclaredTaskInputFileProperty property : declaredFileProperties) {
            property.validate(context);
        }
    }

    private TaskInputFilePropertyBuilderInternal addSpec(ValidatingValue paths, ValidationAction validationAction) {
        DefaultTaskInputFilePropertySpec spec = new DefaultTaskInputFilePropertySpec(task.getName(), resolver, paths, validationAction);
        declaredFileProperties.add(spec);
        return spec;
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (PropertyValue property : properties.values()) {
            String propertyName = property.resolveName();
            try {
                Object value = prepareValue(property.resolveValue());
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

    @Override
    public TaskInputPropertyBuilder property(final String name, @Nullable final Object value) {
        return taskMutator.mutate("TaskInputs.property(String, Object)", new Callable<TaskInputPropertyBuilder>() {
            @Override
            public TaskInputPropertyBuilder call() {
                return registerProperty(name, new StaticValue(value));
            }
        });
    }

    @Override
    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, ?> entry : newProps.entrySet()) {
                    registerProperty(entry.getKey(), new StaticValue(entry.getValue()));
                }
            }
        });
        return deprecatedThis;
    }

    @Override
    public TaskInputPropertyBuilder registerProperty(String name, ValidatingValue value) {
        PropertyValue propertyValue = properties.get(name);
        DeclaredTaskInputProperty spec;
        if (propertyValue instanceof SimplePropertyValue) {
            spec = propertyValue.getPropertySpec();
            propertyValue.setValue(value);
        } else {
            spec = new DefaultTaskInputPropertySpec(this, name, value);
            propertyValue = new SimplePropertyValue(spec, value);
            properties.put(name, propertyValue);
        }
        return spec;
    }

    @Override
    public TaskInputPropertyBuilder registerNested(String name, ValidatingValue value) {
        PropertyValue propertyValue = properties.get(name);
        DeclaredTaskInputProperty spec;
        if (propertyValue instanceof NestedBeanTypePropertyValue) {
            spec = propertyValue.getPropertySpec();
            propertyValue.setValue(value);
        } else {
            spec = new DefaultTaskInputPropertySpec(this, name, value);
            propertyValue = new NestedBeanTypePropertyValue(spec, value);
            properties.put(name, propertyValue);
        }
        return spec;
    }

    private static abstract class PropertyValue {
        protected final DeclaredTaskInputProperty propertySpec;
        protected ValidatingValue value;

        public PropertyValue(DeclaredTaskInputProperty propertySpec, ValidatingValue value) {
            this.propertySpec = propertySpec;
            this.value = value;
        }

        public DeclaredTaskInputProperty getPropertySpec() {
            return propertySpec;
        }

        public abstract String resolveName();

        @Nullable
        public abstract Object resolveValue();

        public void setValue(ValidatingValue value) {
            this.value = value;
        }
    }

    private static class SimplePropertyValue extends PropertyValue {
        public SimplePropertyValue(DeclaredTaskInputProperty propertySpec, ValidatingValue value) {
            super(propertySpec, value);
        }

        @Override
        public String resolveName() {
            return propertySpec.getPropertyName();
        }

        @Override
        public Object resolveValue() {
            return value.call();
        }
    }

    private static class NestedBeanTypePropertyValue extends PropertyValue {
        public NestedBeanTypePropertyValue(DeclaredTaskInputProperty propertySpec, ValidatingValue value) {
            super(propertySpec, value);
        }

        @Override
        public String resolveName() {
            return propertySpec.getPropertyName() + ".class";
        }

        @Override
        public Object resolveValue() {
            Object value = this.value.call();
            return value == null ? null : value.getClass().getName();
        }
    }

    private static class TaskInputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final boolean skipWhenEmptyOnly;
        private final String taskName;
        private final String type;
        private final List<DeclaredTaskInputFileProperty> filePropertiesInternal;

        public TaskInputUnionFileCollection(String taskName, String type, boolean skipWhenEmptyOnly, List<DeclaredTaskInputFileProperty> filePropertiesInternal) {
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
            for (DeclaredTaskInputFileProperty fileProperty : filePropertiesInternal) {
                if (!skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                    context.add(fileProperty.getPropertyFiles());
                }
            }
        }
    }

    private static final ValidationAction INPUT_FILE_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File file = toFile(context, value);
            if (!file.exists()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' does not exist.", file, propertyName));
            } else if (!file.isFile()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' is not a file.", file, propertyName));
            }
        }
    };

    private static final ValidationAction INPUT_DIRECTORY_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File directory = toDirectory(context, value);
            if (!directory.exists()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' does not exist.", directory, propertyName));
            } else if (!directory.isDirectory()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' is not a directory.", directory, propertyName));
            }
        }
    };

    private static final ValidationAction RUNTIME_INPUT_FILE_VALIDATOR = wrapRuntimeApiValidator("file", INPUT_FILE_VALIDATOR);

    private static final ValidationAction RUNTIME_INPUT_DIRECTORY_VALIDATOR = wrapRuntimeApiValidator("dir", INPUT_DIRECTORY_VALIDATOR);

    private static ValidationAction wrapRuntimeApiValidator(final String method, final ValidationAction validator) {
        return new ValidationAction() {
            @Override
            public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
                try {
                    validator.validate(propertyName, value, context, severity);
                } catch (UnsupportedNotationException ex) {
                    DeprecationLogger.nagUserOfDeprecated("Using TaskInputs." + method + "() with something that doesn't resolve to a File object", "Use TaskInputs.files() instead");
                }
            }
        };
    }


    private static File toFile(TaskValidationContext context, Object value) {
        return context.getResolver().resolve(value);
    }

    private static File toDirectory(TaskValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

    private static class FileTreeValue implements ValidatingValue {
        private final ValidatingValue delegate;
        private final FileTreeInternal fileTree;

        public FileTreeValue(ValidatingValue delegate, FileTreeInternal fileTree) {
            this.delegate = delegate;
            this.fileTree = fileTree;
        }

        @Nullable
        @Override
        public Object call() {
            return fileTree;
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            delegate.validate(propertyName, optional, valueValidator, context);
        }
    }
}
