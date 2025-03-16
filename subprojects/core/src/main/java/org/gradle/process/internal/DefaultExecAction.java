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
    private boolean ignoreExitValue;

    public DefaultExecAction(ClientExecHandleBuilder execHandleBuilder) {
        this.execHandleBuilder = execHandleBuilder;
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = execHandleBuilder.build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!isIgnoreExitValue()) {
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
        execHandleBuilder.setStandardInput(inputStream);
        return this;
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
    public OutputStream getStandardOutput() {
        return execHandleBuilder.getStandardOutput();
    }

    @Override
    public BaseExecSpec setErrorOutput(OutputStream outputStream) {
        execHandleBuilder.setErrorOutput(outputStream);
        return this;
    }

    @Override
    public OutputStream getErrorOutput() {
        return execHandleBuilder.getErrorOutput();
    }

    @Override
    public List<String> getCommandLine() {
        return execHandleBuilder.getCommandLine();
    }

    @Override
    public InputStream getStandardInput() {
        return execHandleBuilder.getStandardInput();
    }

    @Override
    public ExecAction setStandardOutput(OutputStream outputStream) {
        execHandleBuilder.setStandardOutput(outputStream);
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
