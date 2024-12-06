/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.provider.sources.process;

import org.gradle.api.Describable;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class ProcessOutputValueSource implements ValueSource<ProcessOutputValueSource.ExecOutputData, ProcessOutputValueSource.Parameters>, Describable {
    public interface Parameters extends ValueSourceParameters {
        /**
         * The full command line of the process to be executed, including all parameters and
         * executable name. This is a mandatory property.
         *
         * @return the command line property
         */
        ListProperty<String> getCommandLine();

        /**
         * The full environment to use when running a process.
         *
         * @return the full environment property, can be not set
         * @see BaseExecSpec#getEnvironment()
         */
        MapProperty<String, Object> getEnvironment();

        /**
         * The working directory of the process. The current directory is used if unset.
         *
         * @return the working directory property
         */
        DirectoryProperty getWorkingDirectory();

        /**
         * Whether the exception should be thrown if the process has non-successful exit code.
         *
         * @return the property to ignore exit value
         * @see org.gradle.process.BaseExecSpec#getIgnoreExitValue()
         */
        Property<Boolean> getIgnoreExitValue();

        static String getExecutable(Parameters p) {
            List<String> command = p.getCommandLine().get();
            return !command.isEmpty() ? command.get(0) : "";
        }
    }

    private final ExecOperations execOperations;

    @Inject
    public ProcessOutputValueSource(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Nullable
    @Override
    public ExecOutputData obtain() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ExecResult r = execOperations.exec(spec -> {
            spec.commandLine(getParameters().getCommandLine().get());
            spec.getIgnoreExitValue().set(getParameters().getIgnoreExitValue().orElse(false));

            if (getParameters().getWorkingDirectory().isPresent()) {
                spec.getWorkingDir().set(getParameters().getWorkingDirectory());
            }
            // TODO(mlopatkin): Check if this logic ok for Gradle 9.0
            // For some reason getParameters().getEnvironment() is MapProperty<Object, Object> at runtime so we need to map it
            spec.getEnvironment().set(getParameters().getEnvironment().map(map -> map));
            spec.getStandardOutput().set(stdout);
            spec.getErrorOutput().set(stderr);
        });
        return new ExecOutputData(r, stdout.toByteArray(), stderr.toByteArray());
    }

    @Override
    public String getDisplayName() {
        return "output of the external process '" + Parameters.getExecutable(getParameters()) + "'";
    }

    public static class ExecOutputData {
        private final ExecResult result;
        private final byte[] output;
        private final byte[] error;

        public ExecOutputData(ExecResult result, byte[] output, byte[] error) {
            this.result = result;
            this.output = output;
            this.error = error;
        }

        public ExecResult getResult() {
            return result;
        }

        public byte[] getOutput() {
            return output.clone();
        }

        public byte[] getError() {
            return error.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ExecOutputData that = (ExecOutputData) o;
            return result.getExitValue() == that.result.getExitValue() && Arrays.equals(output, that.output) && Arrays.equals(error, that.error);
        }

        @Override
        public int hashCode() {
            int hash = Objects.hash(result);
            hash = 31 * hash + Arrays.hashCode(output);
            hash = 31 * hash + Arrays.hashCode(error);
            return hash;
        }
    }
}
