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
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.ProcessForkOptions;

import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Use {@link ExecActionFactory} (for core code) or {@link org.gradle.process.ExecOperations} (for plugin code) instead.
 *
 * TODO: We should remove setters and have abstract getters in Gradle 9.0 and configure builder in execute() method.
 */
public class DefaultExecAction implements ExecAction {

    private final ClientExecHandleBuilder execHandleBuilder;
    private final Property<Boolean> ignoreExitValue;
    private final Property<InputStream> standardInput;
    private final Property<OutputStream> standardOutput;
    private final Property<OutputStream> errorOutput;
    private final Property<String> executable;
    private final DirectoryProperty workingDir;
    private final MapProperty<String, Object> environment;
    private final FileResolver fileResolver;

    @Inject
    public DefaultExecAction(ObjectFactory objectFactory, FileResolver fileResolver, ClientExecHandleBuilder execHandleBuilder) {
        this.fileResolver = fileResolver;
        this.execHandleBuilder = execHandleBuilder;
        this.ignoreExitValue = objectFactory.property(Boolean.class).convention(false);
        this.standardInput = objectFactory.property(InputStream.class);
        this.standardOutput = objectFactory.property(OutputStream.class);
        this.errorOutput = objectFactory.property(OutputStream.class);
        this.executable = objectFactory.property(String.class);
        this.workingDir = objectFactory.directoryProperty();
        this.environment = objectFactory.mapProperty(String.class, Object.class).value(Providers.changing(execHandleBuilder::getDefaultEnvironment));
    }

    @Override
    public ExecAction configure(BaseExecHandleBuilder builder) {
        if (getStandardInput().isPresent()) {
            builder.setStandardInput(getStandardInput().get());
        }
        if (getStandardOutput().isPresent()) {
            builder.setStandardOutput(getStandardOutput().get());
        }
        if (getErrorOutput().isPresent()) {
            builder.setErrorOutput(getErrorOutput().get());
        }
        if (getExecutable().isPresent()) {
            builder.setExecutable(getExecutable().get());
        }
        if (getWorkingDir().isPresent()) {
            builder.setWorkingDir(getWorkingDir().get().getAsFile());
        }
        builder.setEnvironment(getEnvironment().get());
        return this;
    }

    @Override
    public ExecResult execute() {
        configure(execHandleBuilder);
        ExecHandle execHandle = execHandleBuilder.build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!getIgnoreExitValue().get()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }

    @Override
    public Property<String> getExecutable() {
        return executable;
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        if (executable instanceof Provider) {
            getExecutable().set(((Provider<?>) executable).map(Object::toString));
        } else {
            getExecutable().set(Providers.changing((Providers.SerializableCallable<String>) executable::toString));
        }
        return this;
    }

    @Override
    public DirectoryProperty getWorkingDir() {
        return workingDir;
    }

    @Override
    public ExecAction workingDir(Object dir) {
        if (dir instanceof Provider) {
            getWorkingDir().fileProvider(((Provider<?>) dir).map(fileResolver::resolve));
        } else {
            getWorkingDir().set(fileResolver.resolve(dir));
        }
        return this;
    }

    @Override
    public ExecAction commandLine(Object... arguments) {
        execHandleBuilder.commandLine(arguments);
        return this;
    }

    @Override
    public ExecAction commandLine(Iterable<?> args) {
        execHandleBuilder.commandLine(args);
        return this;
    }

    @Override
    public void setCommandLine(List<String> args) {
        execHandleBuilder.commandLine(args);
    }

    @Override
    public void setCommandLine(Object... args) {
        execHandleBuilder.commandLine(args);
    }

    @Override
    public void setCommandLine(Iterable<?> args) {
        execHandleBuilder.commandLine(args);
    }

    @Override
    public ExecAction args(Object... args) {
        execHandleBuilder.args(args);
        return this;
    }

    @Override
    public ExecAction args(Iterable<?> args) {
        execHandleBuilder.args(args);
        return this;
    }

    @Override
    public ExecAction setArgs(List<String> arguments) {
        execHandleBuilder.setArgs(arguments);
        return this;
    }

    @Override
    public ExecAction setArgs(Iterable<?> arguments) {
        execHandleBuilder.setArgs(arguments);
        return this;
    }

    @Override
    public List<String> getArgs() {
        return execHandleBuilder.getArgs();
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return execHandleBuilder.getArgumentProviders();
    }

    @Override
    public MapProperty<String, Object> getEnvironment() {
        return environment;
    }

    @Override
    public ExecAction environment(Map<String, ?> environmentVariables) {
        environment.putAll(environmentVariables);
        return this;
    }

    @Override
    public ExecAction environment(String name, Object value) {
        environment.put(name, value);
        return this;
    }

    @Override
    public Property<Boolean> getIgnoreExitValue() {
        return ignoreExitValue;
    }

    @Override
    public Property<InputStream> getStandardInput() {
        return standardInput;
    }

    @Override
    public Property<OutputStream> getStandardOutput() {
        return standardOutput;
    }

    @Override
    public Property<OutputStream> getErrorOutput() {
        return errorOutput;
    }

    @Override
    public List<String> getCommandLine() {
        return execHandleBuilder.getCommandLine();
    }

    @Override
    public ExecAction listener(ExecHandleListener listener) {
        execHandleBuilder.listener(listener);
        return this;
    }

    @Override
    public ExecAction copyTo(ProcessForkOptions options) {
        throw new UnsupportedOperationException("Copy to ProcessForkOptions is not supported for ExecAction");
    }
}
