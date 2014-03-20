/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.provider.connection;

import org.gradle.api.logging.LogLevel;
import org.gradle.tooling.internal.adapter.CompatibleIntrospector;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdaptedOperationParameters implements ProviderOperationParameters {

    private final BuildOperationParametersVersion1 delegate;
    private final List<String> tasks;
    
    CompatibleIntrospector introspector;

    public AdaptedOperationParameters(BuildOperationParametersVersion1 operationParameters) {
        this(operationParameters, null);
    }

    public AdaptedOperationParameters(BuildOperationParametersVersion1 delegate, List<String> tasks) {
        this.delegate = delegate;
        this.introspector = new CompatibleIntrospector(delegate);
        this.tasks = tasks == null ? null : new LinkedList<String>(tasks);
    }

    public InputStream getStandardInput(InputStream defaultInput) {
        return maybeGet(defaultInput, "getStandardInput");
    }

    public LogLevel getBuildLogLevel() {
        return new BuildLogLevelMixIn(this).getBuildLogLevel();
    }

    public boolean getVerboseLogging(boolean defaultValue) {
        return introspector.getSafely(defaultValue, "getVerboseLogging");
    }

    public File getJavaHome(File defaultJavaHome) {
        return maybeGet(defaultJavaHome, "getJavaHome");
    }

    public List<String> getJvmArguments(List<String> defaultJvmArgs) {
        return maybeGet(defaultJvmArgs, "getJvmArguments");
    }

    private <T> T maybeGet(T defaultValue, String methodName) {
        T out = introspector.getSafely(defaultValue, methodName);
        if (out == null) {
            return defaultValue;
        }
        return out;
    }

    public File getProjectDir() {
        return delegate.getProjectDir();
    }

    public Boolean isSearchUpwards() {
        return delegate.isSearchUpwards();
    }

    public File getGradleUserHomeDir() {
        return delegate.getGradleUserHomeDir();
    }

    public Boolean isEmbedded() {
        return delegate.isEmbedded();
    }

    public Integer getDaemonMaxIdleTimeValue() {
        return delegate.getDaemonMaxIdleTimeValue();
    }

    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return delegate.getDaemonMaxIdleTimeUnits();
    }

    public long getStartTime() {
        return delegate.getStartTime();
    }

    public OutputStream getStandardOutput() {
        return delegate.getStandardOutput();
    }

    public OutputStream getStandardError() {
        return delegate.getStandardError();
    }

    public ProgressListenerVersion1 getProgressListener() {
        return delegate.getProgressListener();
    }

    public List<String> getArguments(List<String> defaultArguments) {
        return maybeGet(defaultArguments, "getArguments");
    }
    
    public List<String> getTasks() {
        return tasks;
    }

    public List<InternalLaunchable> getLaunchables() {
        return maybeGet(null, "getLaunchables");
    }

}
