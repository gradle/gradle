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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.ProcessForkOptions;

import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Use {@link ExecActionFactory} (for core code) or {@link org.gradle.process.ExecOperations} (for plugin code) instead.
 */
public class DefaultExecAction implements ExecAction {

    private final ExecSpec execSpec;
    private final ClientExecHandleBuilder execHandleBuilder;

    @Inject
    public DefaultExecAction(
        ExecSpec execSpec,
        ClientExecHandleBuilder execHandleBuilder
    ) {
        this.execSpec = execSpec;
        this.execHandleBuilder = execHandleBuilder;
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = execHandleBuilder
            .configureFrom(execSpec)
            .build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!getIgnoreExitValue().get()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }

    @Override
    public Property<String> getExecutable() {
        return execSpec.getExecutable();
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        execSpec.executable(executable);
        return this;
    }

    @Override
    public DirectoryProperty getWorkingDir() {
        return execSpec.getWorkingDir();
    }

    @Override
    public ExecAction workingDir(Object dir) {
        execSpec.workingDir(dir);
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
        return execSpec.getEnvironment();
    }

    @Override
    public ExecAction environment(Map<String, ?> environmentVariables) {
        execSpec.environment(environmentVariables);
        return this;
    }

    @Override
    public ExecAction environment(String name, Object value) {
        execSpec.environment(name, value);
        return this;
    }

    @Override
    public Property<Boolean> getIgnoreExitValue() {
        return execSpec.getIgnoreExitValue();
    }

    @Override
    public Property<InputStream> getStandardInput() {
        return execSpec.getStandardInput();
    }

    @Override
    public Property<OutputStream> getStandardOutput() {
        return execSpec.getStandardOutput();
    }

    @Override
    public Property<OutputStream> getErrorOutput() {
        return execSpec.getErrorOutput();
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
