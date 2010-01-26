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

package org.gradle.util.exec;

import org.apache.commons.lang.StringUtils;
import org.gradle.util.LineBufferingOutputStream;

import java.io.*;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleBuilder {

    private File execDirectory;
    private String execCommand;
    private final List<String> arguments = new ArrayList<String>();
    private int normalTerminationExitCode = 0;
    private Map<String, String> environment = new HashMap<String, String>();
    private long keepWaitingTimeout = 100;
    private OutputStream standardOutput;
    private OutputStream errorOutput;
    private InputStream input = new ByteArrayInputStream(new byte[0]);
    private List<ExecHandleListener> listeners = new ArrayList<ExecHandleListener>();

    public ExecHandleBuilder() {
        standardOutput = new LineBuffer(System.out);
        errorOutput = new LineBuffer(System.err);
        inheritEnvironment();
    }

    public ExecHandleBuilder(File execDirectory) {
        setExecDirectory(execDirectory);
    }

    public ExecHandleBuilder(String execCommand) {
        setExecCommand(execCommand);
    }

    public ExecHandleBuilder(File execDirectory, String execCommand) {
        setExecDirectory(execDirectory);
        setExecCommand(execCommand);
    }

    private void setExecDirectory(File execDirectory) {
        if (execDirectory == null) {
            throw new IllegalArgumentException("execDirectory == null!");
        }
        if (execDirectory.exists() && execDirectory.isFile()) {
            throw new IllegalArgumentException("execDirectory is a file!");
        }
        this.execDirectory = execDirectory;
    }

    public ExecHandleBuilder execDirectory(File execDirectory) {
        setExecDirectory(execDirectory);
        return this;
    }

    public File getExecDirectory() {
        if (execDirectory == null) {
            return new File(".");
        } // current directory
        return execDirectory;
    }

    private void setExecCommand(String execCommand) {
        if (StringUtils.isEmpty(execCommand)) {
            throw new IllegalArgumentException("execCommand == null!");
        }
        this.execCommand = execCommand;
    }

    public ExecHandleBuilder execCommand(String execCommand) {
        setExecCommand(execCommand);
        return this;
    }

    public ExecHandleBuilder execCommand(File execCommand) {
        setExecCommand(execCommand.getAbsolutePath());
        return this;
    }

    public String getExecCommand() {
        return execCommand;
    }

    public ExecHandleBuilder clearArguments() {
        this.arguments.clear();
        return this;
    }

    public ExecHandleBuilder commandLine(String... arguments) {
        execCommand(arguments[0]);
        arguments(Arrays.asList(arguments).subList(1, arguments.length));
        return this;
    }

    public ExecHandleBuilder arguments(String... arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("arguments == null!");
        }
        this.arguments.addAll(Arrays.asList(arguments));
        return this;
    }

    public ExecHandleBuilder arguments(List<String> arguments) {
        this.arguments.addAll(arguments);
        return this;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public ExecHandleBuilder normalTerminationExitCode(int normalTerminationExitCode) {
        this.normalTerminationExitCode = normalTerminationExitCode;
        return this;
    }

    public ExecHandleBuilder prependedStringArguments(String prefix, List<String> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("arguments == null!");
        }
        for (String argument : arguments) {
            this.arguments.add(prefix + argument);
        }
        return this;
    }

    public ExecHandleBuilder prependedFileArguments(String prefix, List<File> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("arguments == null!");
        }
        for (File argument : arguments) {
            this.arguments.add(prefix + argument.getAbsolutePath());
        }
        return this;
    }

    public ExecHandleBuilder environment(String key, String value) {
        environment.put(key, value);
        return this;
    }

    public ExecHandleBuilder environment(Map<String, String> values) {
        environment.putAll(values);
        return this;
    }

    public ExecHandleBuilder clearEnvironment() {
        this.environment.clear();
        return this;
    }

    public ExecHandleBuilder inheritEnvironment() {
        clearEnvironment();
        environment.putAll(System.getenv());
        return this;
    }

    public ExecHandleBuilder keepWaitingTimeout(long keepWaitingTimeout) {
        if (keepWaitingTimeout <= 0) {
            throw new IllegalArgumentException("keepWaitingTimeout <= 0!");
        }
        this.keepWaitingTimeout = keepWaitingTimeout;
        return this;
    }

    public ExecHandleBuilder standardInput(InputStream inputStream) {
        this.input = inputStream;
        return this;
    }

    public ExecHandleBuilder standardOutput(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream == null!");
        }
        this.standardOutput = outputStream;
        return this;
    }

    public ExecHandleBuilder errorOutput(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream == null!");
        }
        this.errorOutput = outputStream;
        return this;
    }

    public ExecHandleBuilder clearListeners() {
        this.listeners.clear();
        return this;
    }

    public ExecHandleBuilder listener(ExecHandleListener listener) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners == null!");
        }
        this.listeners.add(listener);
        return this;
    }

    public ExecHandle getExecHandle() {
        if (StringUtils.isEmpty(execCommand)) {
            throw new IllegalStateException("execCommand == null!");
        }

        return new DefaultExecHandle(execDirectory, execCommand, arguments, normalTerminationExitCode, environment,
                keepWaitingTimeout, standardOutput, errorOutput, input, listeners);
    }

    private static class LineBuffer extends LineBufferingOutputStream {
        private static final byte[] EOL = System.getProperty("line.separator").getBytes();
        private final OutputStream target;

        private LineBuffer(OutputStream target) {
            this.target = target;
        }

        @Override
        protected void writeLine(String message) throws IOException {
            target.write(message.getBytes());
            target.write(EOL);
        }
    }
}
