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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Executes a command line process. Example:
 * <pre autoTested=''>
 * task stopTomcat(type:Exec) {
 *   workingDir '../tomcat/bin'
 *
 *   //on windows:
 *   commandLine 'cmd', '/c', 'stop.bat'
 *
 *   //on linux
 *   commandLine './stop.sh'
 *
 *   //store the output instead of printing to the console:
 *   standardOutput = new ByteArrayOutputStream()
 *
 *   //extension method stopTomcat.output() can be used to obtain the output:
 *   ext.output = {
 *     return standardOutput.toString()
 *   }
 * }
 * </pre>
 */
public class Exec extends ConventionTask implements ExecSpec {
    private ExecAction execAction;
    private ExecResult execResult;

    public Exec() {
        execAction = getServices().get(ExecActionFactory.class).newExecAction();
    }

    @TaskAction
    void exec() {
        execResult = execAction.execute();
    }

    /**
     * {@inheritDoc}
     */
    public Exec commandLine(Object... arguments) {
        execAction.commandLine(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ExecSpec commandLine(Iterable<?> args) {
        execAction.commandLine(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec args(Object... args) {
        execAction.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ExecSpec args(Iterable<?> args) {
        execAction.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec setArgs(Iterable<?> arguments) {
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
    public OutputStream getStandardOutput() {
        return execAction.getStandardOutput();
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
    public OutputStream getErrorOutput() {
        return execAction.getErrorOutput();
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
     * Returns the result for the command run by this task. Returns {@code null} if this task has not been executed yet.
     *
     * @return The result. Returns {@code null} if this task has not been executed yet.
     */
    public ExecResult getExecResult() {
        return execResult;
    }
}
