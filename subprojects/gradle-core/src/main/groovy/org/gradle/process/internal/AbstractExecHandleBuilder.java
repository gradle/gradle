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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.process.BaseExecSpec;
import org.gradle.util.GUtil;
import org.gradle.util.LineBufferingOutputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Hans Dockter
 */
public abstract class AbstractExecHandleBuilder extends DefaultProcessForkOptions implements BaseExecSpec {
    private int normalTerminationExitCode;
    private OutputStream standardOutput;
    private OutputStream errorOutput;
    private InputStream input = new ByteArrayInputStream(new byte[0]);
    private List<ExecHandleListener> listeners = new ArrayList<ExecHandleListener>();
    boolean ignoreExitValue;

    public AbstractExecHandleBuilder(FileResolver fileResolver) {
        super(fileResolver);
        standardOutput = new LineBuffer(System.out);
        errorOutput = new LineBuffer(System.err);
    }

    public abstract List<String> getAllArguments();

    public List<String> getCommandLine() {
        return GUtil.addLists(Collections.singleton(getExecutable()), getAllArguments());
    }

    public AbstractExecHandleBuilder normalTerminationExitCode(int normalTerminationExitCode) {
        this.normalTerminationExitCode = normalTerminationExitCode;
        return this;
    }

    public AbstractExecHandleBuilder setStandardInput(InputStream inputStream) {
        this.input = inputStream;
        return this;
    }

    public InputStream getStandardInput() {
        return input;
    }

    public AbstractExecHandleBuilder setStandardOutput(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream == null!");
        }
        this.standardOutput = outputStream;
        return this;
    }

    public AbstractExecHandleBuilder setErrorOutput(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream == null!");
        }
        this.errorOutput = outputStream;
        return this;
    }

    public boolean isIgnoreExitValue() {
        return ignoreExitValue;
    }

    public BaseExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        this.ignoreExitValue = ignoreExitValue;
        return this;
    }

    public AbstractExecHandleBuilder listener(ExecHandleListener listener) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners == null!");
        }
        this.listeners.add(listener);
        return this;
    }
    
    public ExecHandle build() {
        if (StringUtils.isEmpty(getExecutable())) {
            throw new IllegalStateException("execCommand == null!");
        }

        return new DefaultExecHandle(getWorkingDir(), getExecutable(), getAllArguments(), normalTerminationExitCode, getActualEnvironment(),
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
