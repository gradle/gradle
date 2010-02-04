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
import org.gradle.api.internal.tasks.util.DefaultProcessForkOptions;
import org.gradle.util.GUtil;
import org.gradle.util.LineBufferingOutputStream;

import java.io.*;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleBuilder extends DefaultProcessForkOptions {
    private final List<String> arguments = new ArrayList<String>();
    private int normalTerminationExitCode;
    private OutputStream standardOutput;
    private OutputStream errorOutput;
    private InputStream input = new ByteArrayInputStream(new byte[0]);
    private List<ExecHandleListener> listeners = new ArrayList<ExecHandleListener>();

    public ExecHandleBuilder() {
        super(null);
        standardOutput = new LineBuffer(System.out);
        errorOutput = new LineBuffer(System.err);
    }

    public ExecHandleBuilder(File execDirectory) {
        this();
        setWorkingDir(execDirectory);
    }

    public ExecHandleBuilder(String execCommand) {
        this();
        setExecutable(execCommand);
    }

    public ExecHandleBuilder(File execDirectory, String execCommand) {
        this();
        setWorkingDir(execDirectory);
        setExecutable(execCommand);
    }

    public ExecHandleBuilder commandLine(String... arguments) {
        executable(arguments[0]);
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

    public ExecHandleBuilder setArguments(List<String> arguments) {
        this.arguments.clear();
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

    public ExecHandleBuilder standardInput(InputStream inputStream) {
        this.input = inputStream;
        return this;
    }

    public InputStream getStandardInput() {
        return input;
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

    public ExecHandleBuilder listener(ExecHandleListener listener) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners == null!");
        }
        this.listeners.add(listener);
        return this;
    }

    public List<String> getCommandLine() {
        return GUtil.addLists(Collections.singleton(getExecutable()), getArguments());
    }

    public ExecHandle build() {
        if (StringUtils.isEmpty(getExecutable())) {
            throw new IllegalStateException("execCommand == null!");
        }

        return new DefaultExecHandle(getWorkingDir(), getExecutable(), getArguments(), normalTerminationExitCode, getEnvironment(),
                standardOutput, errorOutput, input, listeners);
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
