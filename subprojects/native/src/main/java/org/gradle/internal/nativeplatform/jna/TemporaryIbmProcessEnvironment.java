/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.nativeplatform.jna;

import org.gradle.internal.nativeplatform.*;

import java.io.File;
import java.util.Map;

/**
 * Temporary environment for analyzing stability issues with IBM JDK.
 */
public class TemporaryIbmProcessEnvironment implements ProcessEnvironment {
    private final ProcessEnvironment delegate;

    public TemporaryIbmProcessEnvironment(ProcessEnvironment delegate) {
        this.delegate = delegate;
    }

    public Long getPid() throws NativeIntegrationException {
        return delegate.getPid();
    }

    public File getProcessDir() throws NativeIntegrationException {
        throw new NativeIntegrationException("TemporaryIbmProcessEnvironment doesn't implement getProcessDir()");
    }

    public void setProcessDir(File processDir) throws NativeIntegrationException {
        throw new NativeIntegrationException("TemporaryIbmProcessEnvironment doesn't implement setProcessDir()");
    }

    public void setEnvironmentVariable(String name, String value) throws NativeIntegrationException {
        throw new NativeIntegrationException("TemporaryIbmProcessEnvironment doesn't implement setEnvironmentVariable()");
    }

    public void removeEnvironmentVariable(String name) throws NativeIntegrationException {
        throw new NativeIntegrationException("TemporaryIbmProcessEnvironment doesn't implement removeEnvironmentVariable()");
    }

    public Long maybeGetPid() {
        return delegate.maybeGetPid();
    }

    public boolean maybeSetProcessDir(File processDir) {
        return false;
    }

    public boolean maybeSetEnvironment(Map<String, String> source) {
        return false;
    }

    public boolean maybeSetEnvironmentVariable(String name, String value) {
        return false;
    }

    public boolean maybeRemoveEnvironmentVariable(String name) {
        return false;
    }
}
