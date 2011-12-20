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

package org.gradle.tooling.internal.provider.input;

import org.gradle.tooling.internal.CompatibleIntrospector;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * by Szczepan Faber, created at: 12/20/11
 */
public class AdaptedOperationParameters implements ProviderOperationParameters {

    private final BuildOperationParametersVersion1 delegate;

    public AdaptedOperationParameters(BuildOperationParametersVersion1 delegate) {
        this.delegate = delegate;
    }

    public InputStream getStandardInput() {
        //Tooling api means embedded use. We don't want to consume standard input if we don't own the process.
        //Hence we use a dummy input stream by default
        ByteArrayInputStream safeDummy = new ByteArrayInputStream(new byte[0]);
        return new CompatibleIntrospector(delegate).getSafely(safeDummy, "getStandardInput");
    }

    public boolean getVerboseLogging() {
        return new CompatibleIntrospector(delegate).getSafely(false, "getVerboseLogging");
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
}
