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

import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Use {@link ExecHandleFactory} instead.
 */
public class DefaultExecHandleBuilder extends AbstractExecHandleBuilder implements ExecHandleBuilder, ProcessArgumentsSpec.HasExecutable {

    private final ProcessArgumentsSpec argumentsSpec = new ProcessArgumentsSpec(this);

    public DefaultExecHandleBuilder(ObjectFactory objectFactory, PathToFileResolver fileResolver, Executor executor) {
        this(objectFactory, fileResolver, executor, new DefaultBuildCancellationToken());
    }

    public DefaultExecHandleBuilder(ObjectFactory objectFactory, PathToFileResolver fileResolver, Executor executor, BuildCancellationToken buildCancellationToken) {
        super(objectFactory, fileResolver, executor, buildCancellationToken);
    }

    @Override
    public DefaultExecHandleBuilder executable(Object executable) {
        super.executable(executable);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder commandLine(Object... arguments) {
        argumentsSpec.commandLine(arguments);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder commandLine(Iterable<?> args) {
        argumentsSpec.commandLine(args);
        return this;
    }

    @Override
    public void setCommandLine(List<String> args) {
        argumentsSpec.commandLine(args);
    }

    @Override
    public void setCommandLine(Object... args) {
        argumentsSpec.commandLine(args);
    }

    @Override
    public void setCommandLine(Iterable<?> args) {
        argumentsSpec.commandLine(args);
    }

    @Override
    public DefaultExecHandleBuilder args(Object... args) {
        argumentsSpec.args(args);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder args(Iterable<?> args) {
        argumentsSpec.args(args);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setArgs(List<String> arguments) {
        argumentsSpec.setArgs(arguments);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setArgs(Iterable<?> arguments) {
        argumentsSpec.setArgs(arguments);
        return this;
    }

    @Override
    public List<String> getArgs() {
        return argumentsSpec.getArgs();
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return argumentsSpec.getArgumentProviders();
    }

    @Override
    public List<String> getAllArguments() {
        return argumentsSpec.getAllArguments();
    }

    @Override
    public DefaultExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        super.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder workingDir(Object dir) {
        super.workingDir(dir);
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
        super.daemon = daemon;
        return this;
    }
}
