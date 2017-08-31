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
package org.gradle.api.tasks;

import org.gradle.api.internal.ConventionTask;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * {@code AbstractExecTask} is the base class for all exec tasks.
 *
 * @param <T> The concrete type of the class.
 */
public abstract class AbstractExecTask<T extends AbstractExecTask> extends ConventionTask implements ExecSpec {
    private final Class<T> taskType;
    private ExecAction execAction;
    private ExecResult execResult;

    public AbstractExecTask(Class<T> taskType) {
        execAction = getExecActionFactory().newExecAction();
        this.taskType = taskType;
    }

    @Inject
    protected ExecActionFactory getExecActionFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void exec() {
        execResult = execAction.execute();
    }

    /**
     * {@inheritDoc}
     */
    public T commandLine(Object... arguments) {
        execAction.commandLine(arguments);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T commandLine(Iterable<?> args) {
        execAction.commandLine(args);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T args(Object... args) {
        execAction.args(args);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T args(Iterable<?> args) {
        execAction.args(args);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T setArgs(List<String> arguments) {
        execAction.setArgs(arguments);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T setArgs(Iterable<?> arguments) {
        execAction.setArgs(arguments);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Optional @Input
    public List<String> getArgs() {
        return execAction.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public List<String> getCommandLine() {
        return execAction.getCommandLine();
    }

    /**
     * {@inheritDoc}
     */
    public void setCommandLine(List<String> args) {
        execAction.setCommandLine(args);
    }

    /**
     * {@inheritDoc}
     */
    public void setCommandLine(Iterable<?> args) {
        execAction.setCommandLine(args);
    }

    /**
     * {@inheritDoc}
     */
    public void setCommandLine(Object... args) {
        execAction.setCommandLine(args);
    }

    /**
     * {@inheritDoc}
     */
    @Optional @Input
    public String getExecutable() {
        return execAction.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(String executable) {
        execAction.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(Object executable) {
        execAction.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    public T executable(Object executable) {
        execAction.executable(executable);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    // TODO:LPTR Should be a content-less @InputDirectory
    public File getWorkingDir() {
        return execAction.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(File dir) {
        execAction.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(Object dir) {
        execAction.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public T workingDir(Object dir) {
        execAction.workingDir(dir);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public Map<String, Object> getEnvironment() {
        return execAction.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnvironment(Map<String, ?> environmentVariables) {
        execAction.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    public T environment(String name, Object value) {
        execAction.environment(name, value);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T environment(Map<String, ?> environmentVariables) {
        execAction.environment(environmentVariables);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T copyTo(ProcessForkOptions target) {
        execAction.copyTo(target);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    public T setStandardInput(InputStream inputStream) {
        execAction.setStandardInput(inputStream);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public InputStream getStandardInput() {
        return execAction.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    public T setStandardOutput(OutputStream outputStream) {
        execAction.setStandardOutput(outputStream);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public OutputStream getStandardOutput() {
        return execAction.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    public T setErrorOutput(OutputStream outputStream) {
        execAction.setErrorOutput(outputStream);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public OutputStream getErrorOutput() {
        return execAction.getErrorOutput();
    }

    /**
     * {@inheritDoc}
     */
    public T setIgnoreExitValue(boolean ignoreExitValue) {
        execAction.setIgnoreExitValue(ignoreExitValue);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public boolean isIgnoreExitValue() {
        return execAction.isIgnoreExitValue();
    }

    void setExecAction(ExecAction execAction) {
        this.execAction = execAction;
    }

    /**
     * Returns the result for the command run by this task. Returns {@code null} if this task has not been executed yet.
     *
     * @return The result. Returns {@code null} if this task has not been executed yet.
     */
    @Internal
    public ExecResult getExecResult() {
        return execResult;
    }
}
