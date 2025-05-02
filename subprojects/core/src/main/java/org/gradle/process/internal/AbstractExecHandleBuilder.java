/*
 * Copyright 2024 the original author or authors.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Deprecated. Will be removed in Gradle 9.0. Kept for now it's subclass is used by the Kotlin plugin.
 */
@Deprecated
public abstract class AbstractExecHandleBuilder implements BaseExecSpec {

    protected final ClientExecHandleBuilder delegate;
    private boolean ignoreExitValue;

    AbstractExecHandleBuilder(ClientExecHandleBuilder delegate) {
        this.delegate = delegate;
    }

    public abstract List<String> getAllArguments();

    @Override
    public List<String> getCommandLine() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(getExecutable());
        commandLine.addAll(getAllArguments());
        return commandLine;
    }

    @Override
    public AbstractExecHandleBuilder setStandardInput(InputStream inputStream) {
        delegate.setStandardInput(inputStream);
        return this;
    }

    @Override
    public InputStream getStandardInput() {
        return delegate.getStandardInput();
    }

    @Override
    public AbstractExecHandleBuilder setStandardOutput(OutputStream outputStream) {
        delegate.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public OutputStream getStandardOutput() {
        return delegate.getStandardOutput();
    }

    @Override
    public AbstractExecHandleBuilder setErrorOutput(OutputStream outputStream) {
        delegate.setErrorOutput(outputStream);
        return this;
    }

    @Override
    public OutputStream getErrorOutput() {
        return delegate.getErrorOutput();
    }

    @Override
    public boolean isIgnoreExitValue() {
        return ignoreExitValue;
    }

    @Override
    public AbstractExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        this.ignoreExitValue = ignoreExitValue;
        return this;
    }

    public AbstractExecHandleBuilder setDisplayName(String displayName) {
        delegate.setDisplayName(displayName);
        return this;
    }

    public AbstractExecHandleBuilder listener(ExecHandleListener listener) {
        delegate.listener(listener);
        return this;
    }

    public AbstractExecHandleBuilder streamsHandler(StreamsHandler streamsHandler) {
        delegate.streamsHandler(streamsHandler);
        return this;
    }

    /**
     * Merge the process' error stream into its output stream
     */
    public AbstractExecHandleBuilder redirectErrorStream() {
        delegate.redirectErrorStream();
        return this;
    }

    public AbstractExecHandleBuilder setTimeout(int timeoutMillis) {
        delegate.setTimeout(timeoutMillis);
        return this;
    }

    public ExecHandle build() {
        return delegate.build();
    }
}
