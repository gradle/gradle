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

package org.gradle.process.internal;

import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ProcessForkOptions;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Deprecated. Use {@link ClientExecHandleBuilder} instead. Kept for now since it's used by the Kotlin plugin.
 *
 * Can be merged with {@link ClientExecHandleBuilder} in Gradle 9.0.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class DefaultExecHandleBuilder extends AbstractExecHandleBuilder implements ExecHandleBuilder, ProcessArgumentsSpec.HasExecutable {

    public DefaultExecHandleBuilder(ClientExecHandleBuilder delegate) {
        super(delegate);
    }

    @Override
    public String getExecutable() {
        return delegate.getExecutable();
    }

    @Override
    public void setExecutable(String executable) {
        delegate.setExecutable(executable);
    }

    @Override
    public void setExecutable(Object executable) {
        delegate.setExecutable(executable);
    }

    @Override
    public DefaultExecHandleBuilder executable(Object executable) {
        delegate.setExecutable(executable);
        return this;
    }

    @Override
    public File getWorkingDir() {
        return delegate.getWorkingDir();
    }

    @Override
    public void setWorkingDir(File dir) {
        delegate.setWorkingDir(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        delegate.setWorkingDir(dir);
    }

    @Override
    public DefaultExecHandleBuilder commandLine(Object... arguments) {
        delegate.commandLine(arguments);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder commandLine(Iterable<?> args) {
        delegate.commandLine(args);
        return this;
    }

    @Override
    public void setCommandLine(List<String> args) {
        delegate.commandLine(args);
    }

    @Override
    public void setCommandLine(Object... args) {
        delegate.commandLine(args);
    }

    @Override
    public void setCommandLine(Iterable<?> args) {
        delegate.commandLine(args);
    }

    @Override
    public DefaultExecHandleBuilder args(Object... args) {
        delegate.args(args);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder args(Iterable<?> args) {
        delegate.args(args);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setArgs(List<String> arguments) {
        delegate.setArgs(arguments);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setArgs(Iterable<?> arguments) {
        delegate.setArgs(arguments);
        return this;
    }

    @Override
    public List<String> getArgs() {
        return delegate.getArgs();
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return delegate.getArgumentProviders();
    }

    @Override
    public List<String> getAllArguments() {
        return delegate.getAllArguments();
    }

    @Override
    public DefaultExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        super.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder workingDir(Object dir) {
        delegate.setWorkingDir(dir);
        return this;
    }

    @Override
    public Map<String, Object> getEnvironment() {
        return delegate.getEnvironment();
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        delegate.setEnvironment(environmentVariables);
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        delegate.environment(environmentVariables);
        return this;
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        delegate.environment(name, value);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setDisplayName(String displayName) {
        super.setDisplayName(displayName);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder redirectErrorStream() {
        super.redirectErrorStream();
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setStandardOutput(OutputStream outputStream) {
        super.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setStandardInput(InputStream inputStream) {
        super.setStandardInput(inputStream);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder streamsHandler(StreamsHandler streamsHandler) {
        super.streamsHandler(streamsHandler);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder listener(ExecHandleListener listener) {
        super.listener(listener);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setTimeout(int timeoutMillis) {
        super.setTimeout(timeoutMillis);
        return this;
    }

    @Override
    public ExecHandleBuilder setDaemon(boolean daemon) {
        delegate.setDaemon(daemon);
        return this;
    }

    @Override
    public ProcessForkOptions copyTo(ProcessForkOptions options) {
        delegate.copyTo(options);
        return this;
    }
}
