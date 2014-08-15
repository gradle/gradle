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
package org.gradle.internal.nativeintegration.jna;

import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.util.Map;

public class UnsupportedEnvironment implements ProcessEnvironment {
    public boolean maybeSetEnvironment(Map<String, String> source) {
        return false;
    }

    public void removeEnvironmentVariable(String name) throws NativeIntegrationException {
        throw notSupported();
    }

    public boolean maybeRemoveEnvironmentVariable(String name) {
        return false;
    }

    public void setEnvironmentVariable(String name, String value) throws NativeIntegrationException {
        throw notSupported();
    }

    public boolean maybeSetEnvironmentVariable(String name, String value) {
        return false;
    }

    public File getProcessDir() throws NativeIntegrationException {
        throw notSupported();
    }

    public void setProcessDir(File processDir) throws NativeIntegrationException {
        throw notSupported();
    }

    public boolean maybeSetProcessDir(File processDir) {
        return false;
    }

    public Long getPid() throws NativeIntegrationException {
        throw notSupported();
    }

    public Long maybeGetPid() {
        return null;
    }

    private NativeIntegrationException notSupported() {
        return new NativeIntegrationUnavailableException("We don't support this operating system: " + OperatingSystem.current());
    }
}
