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
package org.gradle.internal.nativeplatform.jna;

import com.sun.jna.Native;
import org.gradle.internal.nativeplatform.NativeIntegrationException;
import org.gradle.internal.nativeplatform.processenvironment.AbstractProcessEnvironment;

import java.io.File;

/**
 * Uses JNA to drive the functions provided by kernel32.dll
 */
public class WindowsProcessEnvironment extends AbstractProcessEnvironment {
    private static final int LOTS_OF_CHARS = 2048;
    private final Kernel32 kernel32 = Kernel32.INSTANCE;

    public void setNativeEnvironmentVariable(String name, String value) {
        boolean retval = kernel32.SetEnvironmentVariable(name, value == null ? null : value);
        if (!retval && (kernel32.GetLastError() != 203/*ERROR_ENVVAR_NOT_FOUND*/)) {
            throw new NativeIntegrationException(String.format("Could not set environment variable '%s'. errno: %d", name, kernel32.GetLastError()));
        }
    }

    public void removeNativeEnvironmentVariable(String name) {
        setNativeEnvironmentVariable(name, null);
    }

    public void setNativeProcessDir(File dir) {
        boolean retval = kernel32.SetCurrentDirectory(dir.getAbsolutePath());
        if (!retval) {
            throw new NativeIntegrationException(String.format("Could not set process working directory to '%s'. errno: %d", dir, kernel32.GetLastError()));
        }
    }

    public File getProcessDir() {
        char[] out = new char[LOTS_OF_CHARS];
        int retval = kernel32.GetCurrentDirectory(out.length, out);
        if (retval == 0) {
            throw new NativeIntegrationException(String.format("Could not get process working directory. errno: %d", kernel32.GetLastError()));
        }
        return new File(Native.toString(out));
    }

    public Long getPid() {
        return Long.valueOf(kernel32.GetCurrentProcessId());
    }
}
