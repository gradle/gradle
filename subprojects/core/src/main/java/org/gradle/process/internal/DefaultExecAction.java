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
import org.gradle.api.provider.Property;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.ProcessForkOptions;

import javax.inject.Inject;
import java.io.File;
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

    @Inject
    public DefaultExecAction(ObjectFactory objectFactory, ClientExecHandleBuilder execHandleBuilder) {
        this.execHandleBuilder = execHandleBuilder;
        this.ignoreExitValue = objectFactory.property(Boolean.class).convention(false);
        this.standardInput = objectFactory.property(InputStream.class);
        this.standardOutput = objectFactory.property(OutputStream.class);
        this.errorOutput = objectFactory.property(OutputStream.class);
    }

    @Override
    public ExecResult execute() {
        if (getStandardInput().isPresent()) {
            execHandleBuilder.setStandardInput(getStandardInput().get());
        }
        if (getStandardOutput().isPresent()) {
            execHandleBuilder.setStandardOutput(getStandardOutput().get());
        }
        if (getErrorOutput().isPresent()) {
            execHandleBuilder.setErrorOutput(getErrorOutput().get());
        }

        ExecHandle execHandle = execHandleBuilder.build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!getIgnoreExitValue().get()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }

    @Override
    public String getExecutable() {
        return execHandleBuilder.getExecutable();
    }

    @Override
    public void setExecutable(String executable) {
        execHandleBuilder.setExecutable(executable);
    }

    @Override
    public void setExecutable(Object executable) {
        execHandleBuilder.setExecutable(executable);
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        execHandleBuilder.setExecutable(executable);
        return this;
    }

    @Override
    public File getWorkingDir() {
        return execHandleBuilder.getWorkingDir();
    }

    @Override
    public void setWorkingDir(File dir) {
        execHandleBuilder.setWorkingDir(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        execHandleBuilder.setWorkingDir(dir);
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
    public ExecAction workingDir(Object dir) {
        execHandleBuilder.setWorkingDir(dir);
        return this;
    }

    @Override
    public Map<String, Object> getEnvironment() {
        return execHandleBuilder.getEnvironment();
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        execHandleBuilder.setEnvironment(environmentVariables);
    }

    @Override
    public ExecAction environment(Map<String, ?> environmentVariables) {
        execHandleBuilder.environment(environmentVariables);
        return this;
    }

    @Override
    public ExecAction environment(String name, Object value) {
        execHandleBuilder.environment(name, value);
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
