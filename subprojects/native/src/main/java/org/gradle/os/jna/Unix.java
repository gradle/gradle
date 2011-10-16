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
package org.gradle.os.jna;

import com.sun.jna.Native;
import org.gradle.os.NativeIntegrationException;

import java.io.File;

class Unix extends AbstractNativeEnvironment {
    private static final int LOTS_OF_CHARS = 2048;
    private final UnixLibC libc = (UnixLibC) Native.loadLibrary("c", UnixLibC.class);

    public void setNativeEnvironmentVariable(String name, String value) {
        int retval = libc.setenv(name, value, 1);
        if (retval != 0) {
            throw new NativeIntegrationException(String.format("Could not set environment variable '%s'. errno: %d", name, libc.errno()));
        }
    }

    public void removeNativeEnvironmentVariable(String name) {
        int retval = libc.unsetenv(name);
        if (retval != 0) {
            throw new NativeIntegrationException(String.format("Could not unset environment variable '%s'. errno: %d", name, libc.errno()));
        }
    }

    public void setNativeProcessDir(File dir) {
        int retval = libc.chdir(dir.getAbsolutePath());
        if (retval != 0) {
            throw new NativeIntegrationException(String.format("Could not set process working directory to '%s'. errno: %d", dir, libc.errno()));
        }
    }

    public File getProcessDir() {
        byte[] out = new byte[LOTS_OF_CHARS];
        String retval = libc.getcwd(out, LOTS_OF_CHARS);
        if (retval == null) {
            throw new NativeIntegrationException(String.format("Could not get process working directory. errno: %d", libc.errno()));
        }
        return new File(Native.toString(out));
    }

    public Long getPid() {
        return Long.valueOf(libc.getpid());
    }
}
