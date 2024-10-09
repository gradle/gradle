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

import org.gradle.api.provider.Property;
import org.gradle.process.ProcessForkOptions;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

abstract class ProviderCompatibleBaseExecSpec implements DelegatingBaseExecSpec {
    private final Map<String, Object> additionalEnvVars = new HashMap<>();
    @Nullable
    private Map<String, Object> fullEnvironment;

    @Nullable
    private File workingDirectory;

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

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        fullEnvironment = new HashMap<>(environmentVariables);
        // We are replacing the environment completely, there is no need to keep previous additions.
        additionalEnvVars.clear();
        // Keep the delegate's view of environment consistent to make sure getEnvironment and
        // copyTo work properly.
        DelegatingBaseExecSpec.super.setEnvironment(fullEnvironment);
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        getMapForAppends().putAll(environmentVariables);
        // Keep the delegate's view of environment consistent to make sure getEnvironment and
        // copyTo work properly.
        DelegatingBaseExecSpec.super.environment(environmentVariables);
        return this;
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        getMapForAppends().put(name, value);
        // Keep the delegate's view of environment consistent to make sure getEnvironment and
        // copyTo work properly.
        DelegatingBaseExecSpec.super.environment(name, value);
        return this;
    }

    private Map<String, Object> getMapForAppends() {
        // If the full environment is specified then additionalEnvVars isn't used, all
        // additions go into the full map directly.
        return (fullEnvironment != null) ? fullEnvironment : additionalEnvVars;
    }

    @Override
    public void setWorkingDir(File dir) {
        DelegatingBaseExecSpec.super.setWorkingDir(dir);
        workingDirectory = getWorkingDir();
    }

    @Override
    public void setWorkingDir(Object dir) {
        DelegatingBaseExecSpec.super.setWorkingDir(dir);
        workingDirectory = getWorkingDir();
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        DelegatingBaseExecSpec.super.workingDir(dir);
        workingDirectory = getWorkingDir();
        return this;
    }

    @Override
    public ProcessForkOptions copyTo(ProcessForkOptions options) {
        return DelegatingBaseExecSpec.super.copyTo(options);
    }

    public void copyToParameters(ProcessOutputValueSource.Parameters parameters) {
        parameters.getCommandLine().set(getCommandLine());
        parameters.getFullEnvironment().set(fullEnvironment);
        parameters.getAdditionalEnvironmentVariables().set(additionalEnvVars.isEmpty() ? null : additionalEnvVars);
        parameters.getWorkingDirectory().set(workingDirectory);
        parameters.getIgnoreExitValue().set(getIgnoreExitValue());
    }
}
