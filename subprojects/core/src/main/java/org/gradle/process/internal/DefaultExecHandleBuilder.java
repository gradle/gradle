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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ProcessForkOptions;

import java.util.List;
import java.util.Map;

/**
 * Deprecated. Use {@link ClientExecHandleBuilder} instead. Kept for now since it's used by the Kotlin plugin.
 *
 * Can be merged with {@link ClientExecHandleBuilder} in Gradle 9.0.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class DefaultExecHandleBuilder extends AbstractExecHandleBuilder implements ExecHandleBuilder {

    private final FileResolver resolver;

    public DefaultExecHandleBuilder(ObjectFactory objectFactory, FileResolver resolver, ClientExecHandleBuilder delegate) {
        super(objectFactory, delegate);
        this.resolver = resolver;
    }

    @Override
    public Property<String> getExecutable() {
        return super.getExecutable();
    }

    @Override
    public DefaultExecHandleBuilder executable(Object executable) {
        super.executable(executable);
        return this;
    }

    @Override
    public DirectoryProperty getWorkingDir() {
        return workingDir;
    }

    @Override
    public DefaultExecHandleBuilder workingDir(Object dir) {
        if (dir instanceof Provider) {
            getWorkingDir().fileProvider(((Provider<?>) dir).map(resolver::resolve));
        } else {
            getWorkingDir().set(resolver.resolve(dir));
        }
        return this;
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
    public MapProperty<String, Object> getEnvironment() {
        return super.getEnvironment();
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        getEnvironment().putAll(environmentVariables);
        return this;
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        getEnvironment().put(name, value);
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
