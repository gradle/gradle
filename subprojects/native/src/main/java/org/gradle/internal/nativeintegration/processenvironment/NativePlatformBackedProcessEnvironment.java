/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.nativeintegration.processenvironment;

import net.rubygrapefruit.platform.Process;

import java.io.File;

public class NativePlatformBackedProcessEnvironment extends AbstractProcessEnvironment {
    private final Process process;

    public NativePlatformBackedProcessEnvironment(Process process) {
        this.process = process;
    }

    @Override
    protected void removeNativeEnvironmentVariable(String name) {
        process.setEnvironmentVariable(name, null);
    }

    @Override
    protected void setNativeEnvironmentVariable(String name, String value) {
        process.setEnvironmentVariable(name, value);
    }

    @Override
    protected void setNativeProcessDir(File processDir) {
        process.setWorkingDirectory(processDir);
    }

    @Override
    public File getProcessDir() {
        return process.getWorkingDirectory();
    }

    @Override
    public Long getPid() {
        return (long) process.getProcessId();
    }

    @Override
    public void detachProcess() {
        process.detach();
    }
}
