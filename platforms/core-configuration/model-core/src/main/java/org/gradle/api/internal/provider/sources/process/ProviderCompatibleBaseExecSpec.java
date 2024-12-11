/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.provider.sources.process;

import org.gradle.api.file.Directory;
import org.gradle.api.provider.Property;
import org.gradle.process.BaseExecSpec;

import java.io.InputStream;
import java.io.OutputStream;

abstract class ProviderCompatibleBaseExecSpec<T extends BaseExecSpec> implements DelegatingBaseExecSpec {

    private final T delegate;
    private final Directory defaultWorkingDir;

    public ProviderCompatibleBaseExecSpec(T baseExecSpec) {
        this.delegate = baseExecSpec;
        this.defaultWorkingDir = delegate.getWorkingDir().get();
    }

    @Override
    public T getDelegate() {
        return this.delegate;
    }

    @Override
    public Property<InputStream> getStandardInput() {
        throw new UnsupportedOperationException("Standard streams cannot be configured for exec output provider");
    }

    @Override
    public Property<OutputStream> getStandardOutput() {
        throw new UnsupportedOperationException("Standard streams cannot be configured for exec output provider");
    }

    @Override
    public Property<OutputStream> getErrorOutput() {
        throw new UnsupportedOperationException("Standard streams cannot be configured for exec output provider");
    }

    public void copyToParameters(ProcessOutputValueSource.Parameters parameters) {
        parameters.getCommandLine().set(getCommandLine());
        parameters.getEnvironment().set(getEnvironment());
        // TODO(mlopatkin): Check this logic for Gradle 9
        // Only pass the working directory if it is different from the default
        if (!defaultWorkingDir.getAsFile().equals(getWorkingDir().getAsFile().get())) {
            parameters.getWorkingDirectory().set(getWorkingDir());
        }
        parameters.getIgnoreExitValue().set(getIgnoreExitValue());
    }
}
