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

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import org.gradle.internal.nativeplatform.NativeIntegrationException;
import org.gradle.internal.nativeplatform.processenvironment.AbstractProcessEnvironment;

import java.io.File;

/**
 * Uses JNA to drive the POSIX API provided by libc
 */
public class LibCBackedProcessEnvironment extends AbstractProcessEnvironment {
    private static final int LOTS_OF_CHARS = 2048;
    private final LibC libc;

    public LibCBackedProcessEnvironment(LibC libc) {
        this.libc = libc;
    }

    public void setNativeEnvironmentVariable(String name, String value) {
        try {
            libc.setenv(name, value, 1);
        } catch (LastErrorException lastErrorException) {
            throw new NativeIntegrationException(String.format("Could not set environment variable '%s'. errno: %d", name, lastErrorException.getErrorCode()));
        }
    }

    public void removeNativeEnvironmentVariable(String name) {
        setNativeEnvironmentVariable(name, "");
    }

    public void setNativeProcessDir(File dir) {
        try {
            libc.chdir(dir.getAbsolutePath());
        } catch (LastErrorException lastErrorException) {
            throw new NativeIntegrationException(String.format("Could not set process working directory to '%s'. errno: %d", dir, lastErrorException.getErrorCode()));
        }
    }

    public File getProcessDir() {
        byte[] out = new byte[LOTS_OF_CHARS];
        try {
            libc.getcwd(out, LOTS_OF_CHARS);
        } catch (LastErrorException lastErrorException) {
            throw new NativeIntegrationException(String.format("Could not get process working directory. errno: %d", lastErrorException.getErrorCode()));
        }
        return new File(Native.toString(out));
    }

    public Long getPid() {
        return Long.valueOf(libc.getpid());
    }
}
