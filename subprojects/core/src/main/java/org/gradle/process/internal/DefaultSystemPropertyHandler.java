/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.process.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Named;
import org.gradle.api.tasks.HasStringRepresentation;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.SystemPropertyHandler;
import org.gradle.util.DeferredUtil;

public class DefaultSystemPropertyHandler implements SystemPropertyHandler {
    private final JavaForkOptions javaForkOptions;
    private final PathToFileResolver resolver;

    public DefaultSystemPropertyHandler(JavaForkOptions javaForkOptions, PathToFileResolver resolver) {
        this.javaForkOptions = javaForkOptions;
        this.resolver = resolver;
    }

    @Override
    public void add(String name, HasStringRepresentation value) {
        javaForkOptions.getJvmArgumentProviders().add(new SystemPropertyCommandLineArgument(name, value));
    }

    @Override
    public void add(String name, Object value) {
        javaForkOptions.systemProperty(name, value);
    }

    @Override
    public HasStringRepresentation inputFile(Object file) {
        return new InputFileValue(file);
    }

    @Override
    public HasStringRepresentation inputDirectory(Object directory) {
        return new InputDirectoryValue(directory);
    }

    @Override
    public HasStringRepresentation outputFile(Object file) {
        return new OutputFileValue(file);
    }

    @Override
    public HasStringRepresentation outputDirectory(Object directory) {
        return new OutputDirectoryValue(directory);
    }

    @Override
    public HasStringRepresentation ignored(Object value) {
        return new IgnoredValue(value);
    }

    private class SystemPropertyCommandLineArgument implements CommandLineArgumentProvider, Named {
        private final String name;
        private final HasStringRepresentation value;

        public SystemPropertyCommandLineArgument(String name, HasStringRepresentation value) {
            this.name = name;
            this.value = value;
        }

        @Nested
        public HasStringRepresentation getValue() {
            return value;
        }

        @Input
        @Override
        public String getName() {
            return name;
        }

        @Override
        public Iterable<String> asArguments() {
            return ImmutableList.of("-D" + name + "=" + value.asString());
        }
    }

    private abstract class AbstractValueWithStringRepresentation<T> implements HasStringRepresentation {

        private final T value;

        public AbstractValueWithStringRepresentation(T value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    private class FileAsAbsolutePath extends AbstractValueWithStringRepresentation<Object> {

        public FileAsAbsolutePath(Object value) {
            super(value);
        }

        @Override
        public String asString() {
            return resolver.resolve(getValue()).getAbsolutePath();
        }
    }

    private class InputFileValue extends FileAsAbsolutePath {
        public InputFileValue(Object file) {
            super(file);
        }

        @Override
        @PathSensitive(PathSensitivity.NONE)
        @InputFile
        public Object getValue() {
            return super.getValue();
        }
    }

    private class InputDirectoryValue extends FileAsAbsolutePath {
        public InputDirectoryValue(Object directory) {
            super(directory);
        }

        @Override
        @PathSensitive(PathSensitivity.RELATIVE)
        @InputDirectory
        public Object getValue() {
            return super.getValue();
        }
    }

    private class OutputFileValue extends FileAsAbsolutePath {
        public OutputFileValue(Object file) {
            super(file);
        }

        @Override
        @OutputFile
        public Object getValue() {
            return super.getValue();
        }
    }

    private class OutputDirectoryValue extends FileAsAbsolutePath {
        public OutputDirectoryValue(Object directory) {
            super(directory);
        }

        @Override
        @OutputDirectory
        public Object getValue() {
            return super.getValue();
        }
    }

    private class IgnoredValue extends AbstractValueWithStringRepresentation<Object> {
        public IgnoredValue(Object value) {
            super(value);
        }

        @Override
        @Internal
        public Object getValue() {
            return super.getValue();
        }

        @Override
        public String asString() {
            return Preconditions.checkNotNull(DeferredUtil.unpack(getValue())).toString();
        }
    }
}
