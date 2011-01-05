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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Hans Dockter
 */
public abstract class AbstractExecHandleBuilder extends DefaultProcessForkOptions implements BaseExecSpec {
    private OutputStream standardOutput;
    private OutputStream errorOutput;
    private InputStream input = new ByteArrayInputStream(new byte[0]);
    private String displayName;
    private final List<ExecHandleListener> listeners = new ArrayList<ExecHandleListener>();
    boolean ignoreExitValue;

    public AbstractExecHandleBuilder(FileResolver fileResolver) {
        super(fileResolver);
        standardOutput = System.out;
        errorOutput = System.err;
    }

    public abstract List<String> getAllArguments();

    public List<String> getCommandLine() {
        return GUtil.addLists(Collections.singleton(getExecutable()), getAllArguments());
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

    public OutputStream getStandardOutput() {
        return standardOutput;
    }

    public AbstractExecHandleBuilder setErrorOutput(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream == null!");
        }
        this.errorOutput = outputStream;
        return this;
    }

    public OutputStream getErrorOutput() {
        return errorOutput;
    }

    public boolean isIgnoreExitValue() {
        return ignoreExitValue;
    }

    public BaseExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        this.ignoreExitValue = ignoreExitValue;
        return this;
    }

    public String getDisplayName() {
        return displayName == null ? String.format("command '%s'", getExecutable()) : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public AbstractExecHandleBuilder listener(ExecHandleListener listener) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners == null!");
        }
        this.listeners.add(listener);
        return this;
    }
    
    public ExecHandle build() {
        String executable = getExecutable();
        if (StringUtils.isEmpty(executable)) {
            throw new IllegalStateException("execCommand == null!");
        }

        return new DefaultExecHandle(getDisplayName(), getWorkingDir(), executable, getAllArguments(), getActualEnvironment(),
                standardOutput, errorOutput, input, listeners);
    }
}
