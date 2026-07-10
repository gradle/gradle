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
import org.gradle.process.internal.streams.StreamsHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Deprecated. Will be removed in Gradle 10. Kept for now it's subclass is used by the Kotlin plugin.
 */
@Deprecated
public abstract class AbstractExecHandleBuilder implements BaseExecSpec {

    protected final ExecAction delegate;

    AbstractExecHandleBuilder(ExecAction execAction) {
        this.delegate = execAction;
    }

    @Override
    public List<String> getCommandLine() {
        return delegate.getCommandLine();
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
        return delegate.isIgnoreExitValue();
    }

    @Override
    public AbstractExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        delegate.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    public AbstractExecHandleBuilder setDisplayName(String displayName) {
        throw new UnsupportedOperationException("setDisplayName() is not supported");
    }

    public AbstractExecHandleBuilder listener(ExecHandleListener listener) {
        delegate.listener(listener);
        return this;
    }

    public AbstractExecHandleBuilder streamsHandler(StreamsHandler streamsHandler) {
        throw new UnsupportedOperationException("streamsHandler() is not supported");
    }

    /**
     * Merge the process' error stream into its output stream
     */
    public AbstractExecHandleBuilder redirectErrorStream() {
        throw new UnsupportedOperationException("redirectErrorStream() is not supported");
    }

    public AbstractExecHandleBuilder setTimeout(int timeoutMillis) {
        throw new UnsupportedOperationException("setTimeout() is not supported");
    }

    public ExecHandle build() {
        return delegate.buildHandle();
    }
}
