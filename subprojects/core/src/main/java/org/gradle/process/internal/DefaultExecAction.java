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

import org.gradle.process.BaseExecSpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.ProcessForkOptions;

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
    private final ExecSpec execSpec;
    private boolean ignoreExitValue;

    public DefaultExecAction(ExecSpec execSpec, ClientExecHandleBuilder execHandleBuilder) {
        this.execSpec = execSpec;
        this.execHandleBuilder = execHandleBuilder;
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = buildExecHandle();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!isIgnoreExitValue()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }

    private ExecHandle buildExecHandle() {
        execHandleBuilder
            .commandLine(execSpec.getCommandLine())
            .setWorkingDir(execSpec.getWorkingDir())
            .setEnvironment(execSpec.getEnvironment());
        if (execSpec.getStandardInput() != null) {
            execHandleBuilder.setStandardInput(execSpec.getStandardInput());
        }
        if (execSpec.getStandardOutput() != null) {
            execHandleBuilder.setStandardOutput(execSpec.getStandardOutput());
        }
        if (execSpec.getErrorOutput() != null) {
            execHandleBuilder.setErrorOutput(execSpec.getErrorOutput());
        }
        return execHandleBuilder.build();
    }

    @Override
    public String getExecutable() {
        return execSpec.getExecutable();
    }

    @Override
    public void setExecutable(String executable) {
        execSpec.setExecutable(executable);
    }

    @Override
    public void setExecutable(Object executable) {
        execSpec.setExecutable(executable);
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        execSpec.setExecutable(executable);
        return this;
    }

    @Override
    public File getWorkingDir() {
        return execSpec.getWorkingDir();
    }

    @Override
    public void setWorkingDir(File dir) {
        execSpec.setWorkingDir(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        execSpec.setWorkingDir(dir);
    }

    @Override
    public ExecAction commandLine(Object... arguments) {
        execSpec.commandLine(arguments);
        return this;
    }

    @Override
    public ExecAction commandLine(Iterable<?> args) {
        execSpec.commandLine(args);
        return this;
    }

    @Override
    public void setCommandLine(List<String> args) {
        execSpec.commandLine(args);
    }

    @Override
    public void setCommandLine(Object... args) {
        execSpec.commandLine(args);
    }

    @Override
    public void setCommandLine(Iterable<?> args) {
        execSpec.commandLine(args);
    }

    @Override
    public ExecAction args(Object... args) {
        execSpec.args(args);
        return this;
    }

    @Override
    public ExecAction args(Iterable<?> args) {
        execSpec.args(args);
        return this;
    }

    @Override
    public ExecAction setArgs(List<String> arguments) {
        execSpec.setArgs(arguments);
        return this;
    }

    @Override
    public ExecAction setArgs(Iterable<?> arguments) {
        execSpec.setArgs(arguments);
        return this;
    }

    @Override
    public List<String> getArgs() {
        return execSpec.getArgs();
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return execSpec.getArgumentProviders();
    }

    @Override
    public ExecAction setIgnoreExitValue(boolean ignoreExitValue) {
        this.ignoreExitValue = ignoreExitValue;
        return this;
    }

    @Override
    public boolean isIgnoreExitValue() {
        return ignoreExitValue;
    }

    @Override
    public ExecAction setStandardInput(InputStream inputStream) {
        execSpec.setStandardInput(inputStream);
        return this;
    }

    @Override
    public ExecAction workingDir(Object dir) {
        execSpec.setWorkingDir(dir);
        return this;
    }

    @Override
    public Map<String, Object> getEnvironment() {
        return execSpec.getEnvironment();
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        execSpec.setEnvironment(environmentVariables);
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
    public OutputStream getStandardOutput() {
        return execSpec.getStandardOutput();
    }

    @Override
    public BaseExecSpec setErrorOutput(OutputStream outputStream) {
        execSpec.setErrorOutput(outputStream);
        return this;
    }

    @Override
    public OutputStream getErrorOutput() {
        return execSpec.getErrorOutput();
    }

    @Override
    public List<String> getCommandLine() {
        return execSpec.getCommandLine();
    }

    @Override
    public InputStream getStandardInput() {
        return execSpec.getStandardInput();
    }

    @Override
    public ExecAction setStandardOutput(OutputStream outputStream) {
        execSpec.setStandardOutput(outputStream);
        return this;
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
