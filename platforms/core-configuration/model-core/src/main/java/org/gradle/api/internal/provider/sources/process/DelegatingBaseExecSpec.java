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

import org.gradle.api.provider.Property;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ProcessForkOptions;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Just the wrapper that delegates all calls to what {@link #getDelegate()} returns.
 */
interface DelegatingBaseExecSpec extends BaseExecSpec {
    @Override
    default Property<Boolean> getIgnoreExitValue() {
        return getDelegate().getIgnoreExitValue();
    }

    @Override
    default Property<InputStream> getStandardInput() {
        return getDelegate().getStandardInput();
    }

    @Override
    default Property<OutputStream> getStandardOutput() {
        return getDelegate().getStandardOutput();
    }

    @Override
    default Property<OutputStream> getErrorOutput() {
        return getDelegate().getErrorOutput();
    }

    @Override
    default List<String> getCommandLine() {
        return getDelegate().getCommandLine();
    }

    @Override
    default String getExecutable() {
        return getDelegate().getExecutable();
    }

    @Override
    default void setExecutable(String executable) {
        getDelegate().setExecutable(executable);
    }

    @Override
    default void setExecutable(Object executable) {
        getDelegate().setExecutable(executable);
    }

    @Override
    default ProcessForkOptions executable(Object executable) {
        getDelegate().executable(executable);
        return this;
    }

    @Override
    default File getWorkingDir() {
        return getDelegate().getWorkingDir();
    }

    @Override
    default void setWorkingDir(File dir) {
        getDelegate().setWorkingDir(dir);
    }

    @Override
    default void setWorkingDir(Object dir) {
        getDelegate().setWorkingDir(dir);
    }

    @Override
    default ProcessForkOptions workingDir(Object dir) {
        getDelegate().workingDir(dir);
        return this;
    }

    @Override
    default Map<String, Object> getEnvironment() {
        return getDelegate().getEnvironment();
    }

    @Override
    default void setEnvironment(Map<String, ?> environmentVariables) {
        getDelegate().setEnvironment(environmentVariables);
    }

    @Override
    default ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        getDelegate().environment(environmentVariables);
        return this;
    }

    @Override
    default ProcessForkOptions environment(String name, Object value) {
        getDelegate().environment(name, value);
        return this;
    }

    @Override
    default ProcessForkOptions copyTo(ProcessForkOptions options) {
        getDelegate().copyTo(options);
        return this;
    }

    BaseExecSpec getDelegate();
}
