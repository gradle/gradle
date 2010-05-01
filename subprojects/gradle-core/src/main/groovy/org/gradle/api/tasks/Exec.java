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
import org.gradle.process.internal.DefaultExecAction;
import org.gradle.process.internal.ExecAction;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * A task for executing a command line process.
 * 
 * @author Hans Dockter
 */
public class Exec extends ConventionTask implements ExecSpec {
    private ExecAction execAction;
    private ExecResult execResult;

    public Exec() {
        execAction = new DefaultExecAction();
    }

    @TaskAction
    void exec() {
        execResult = execAction.execute();
    }

    /**
     * {@inheritDoc}
     */
    public Exec commandLine(String... arguments) {
        execAction.commandLine(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec args(String... args) {
        execAction.args(args);
        return this;
    }

    public Exec setArgs(List<String> arguments) {
        execAction.setArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getArgs() {
        return execAction.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getCommandLine() {
        return execAction.getCommandLine();
    }

    /**
     * {@inheritDoc}
     */
    public String getExecutable() {
        return execAction.getExecutable();
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
    public Exec executable(Object executable) {
        execAction.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public File getWorkingDir() {
        return execAction.getWorkingDir();
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
    public Exec workingDir(Object dir) {
        execAction.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
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
    public Exec environment(String name, Object value) {
        execAction.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec environment(Map<String, ?> environmentVariables) {
        execAction.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec copyTo(ProcessForkOptions target) {
        execAction.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec setStandardInput(InputStream inputStream) {
        execAction.setStandardInput(inputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getStandardInput() {
        return execAction.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    public Exec setStandardOutput(OutputStream outputStream) {
        execAction.setStandardOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec setErrorOutput(OutputStream outputStream) {
        execAction.setErrorOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        execAction.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isIgnoreExitValue() {
        return execAction.isIgnoreExitValue();
    }

    void setExecAction(ExecAction execAction) {
        this.execAction = execAction;
    }

    /**
     * Returns the ExecResult object for the command run by this task. Returns null if the task has not been executed yet.
     */
    public ExecResult getExecResult() {
        return execResult;
    }
}
