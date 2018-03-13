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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Named;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.InputOutputValue;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.SystemPropertyHandler;
import org.gradle.util.DeferredUtil;

public class DefaultSystemPropertyHandler implements SystemPropertyHandler {
    private final JavaForkOptions javaForkOptions;

    public DefaultSystemPropertyHandler(JavaForkOptions javaForkOptions) {
        this.javaForkOptions = javaForkOptions;
    }

    @Override
    public void add(String name, InputOutputValue value) {
        javaForkOptions.getJvmArgumentProviders().add(new SystemPropertyCommandLineArgument(name, value));
    }

    @Override
    public void add(String name, Object value) {
        javaForkOptions.systemProperty(name, value);
    }

    @Override
    public InputOutputValue inputFile(Object file) {
        return new InputFileValue(file);
    }

    @Override
    public InputOutputValue inputDirectory(Object file) {
        return new InputDirectoryValue(file);
    }

    @Override
    public InputOutputValue outputFile(Object file) {
        return new OutputFileValue(file);
    }

    @Override
    public InputOutputValue outputDirectory(Object file) {
        return new OutputDirectoryValue(file);
    }

    @Override
    public InputOutputValue ignored(Object file) {
        return new IgnoredValue(file);
    }

    private class SystemPropertyCommandLineArgument implements CommandLineArgumentProvider, Named {
        private final String name;
        private final InputOutputValue inputOutputValue;

        public SystemPropertyCommandLineArgument(String name, InputOutputValue inputOutputValue) {
            this.name = name;
            this.inputOutputValue = inputOutputValue;
        }

        @Nested
        public InputOutputValue getValue() {
            return inputOutputValue;
        }

        @Input
        @Override
        public String getName() {
            return name;
        }

        @Override
        public Iterable<String> asArguments() {
            return ImmutableList.of("-D" + name + "=" + DeferredUtil.unpack(inputOutputValue.getValue()));
        }
    }

    private class BaseInputOutputValue implements InputOutputValue {

        private final Object value;

        public BaseInputOutputValue(Object value) {
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }

    private class InputFileValue extends BaseInputOutputValue {

        public InputFileValue(Object path) {
            super(path);
        }

        @Override
        @PathSensitive(PathSensitivity.NONE)
        @InputFile
        public Object getValue() {
            return super.getValue();
        }
    }

    private class InputDirectoryValue extends BaseInputOutputValue {
        public InputDirectoryValue(Object path) {
            super(path);
        }

        @Override
        @PathSensitive(PathSensitivity.RELATIVE)
        @InputDirectory
        public Object getValue() {
            return super.getValue();
        }
    }

    private class OutputFileValue extends BaseInputOutputValue {
        public OutputFileValue(Object path) {
            super(path);
        }

        @Override
        @OutputFile
        public Object getValue() {
            return super.getValue();
        }
    }

    private class OutputDirectoryValue extends BaseInputOutputValue {
        public OutputDirectoryValue(Object path) {
            super(path);
        }

        @Override
        @OutputDirectory
        public Object getValue() {
            return super.getValue();
        }
    }

    private class IgnoredValue extends BaseInputOutputValue {
        public IgnoredValue(Object value) {
            super(value);
        }

        @Override
        @Internal
        public Object getValue() {
            return super.getValue();
        }
    }
}
