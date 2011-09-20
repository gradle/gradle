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

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;
import org.gradle.os.NativeIntegrationException;
import org.gradle.os.OperatingSystem;
import org.gradle.os.ProcessEnvironment;

import java.io.File;

/**
 * Uses jna to update the environment variables
 *
 * @author: Szczepan Faber, created at: 9/7/11
 */
public class NativeEnvironment {

    //CHECKSTYLE:OFF
    public interface UnixLibC extends Library {
        public int setenv(String name, String value, int overwrite);
        public int unsetenv(String name);
        public String getcwd(byte[] out, int size);
        public int chdir(String dirAbsolutePath);
        public int errno();
    }
    //CHECKSTYLE:ON

    //2 bytes per unicode character, this should be enough to handle sane path lengths
    private static final int LOTS_OF_CHARS = 2048;

    public static ProcessEnvironment current() {
        if (OperatingSystem.current().isUnix()) {
            return new Unix();
        } else if (OperatingSystem.current().isWindows()) {
            return new Windows();
        } else {
            throw new NativeIntegrationException("We don't support this operating system: " + OperatingSystem.current());
        }
    }

    public static class Windows implements ProcessEnvironment {
        private final Kernel32 kernel32 = Kernel32.INSTANCE;

        public void setenv(String name, String value) {
            boolean retval = kernel32.SetEnvironmentVariableW(new WString(name), value == null ? null : new WString(value));
            if (!retval) {
                throw new NativeIntegrationException(String.format("Could not set environment variable '%s'. errno: %d", name, kernel32.GetLastError()));
            }
        }

        public void unsetenv(String name) {
            setenv(name, null);
        }

        public void setProcessDir(File dir) {
            boolean retval = kernel32.SetCurrentDirectoryW(new WString(dir.getAbsolutePath()));
            if (!retval) {
                throw new NativeIntegrationException(String.format("Could not set process working directory to '%s'. errno: %d", dir, kernel32.GetLastError()));
            }
        }

        public File getProcessDir() {
            char[] out = new char[LOTS_OF_CHARS];
            int retval = kernel32.GetCurrentDirectoryW(out.length, out);
            if (retval == 0) {
                throw new NativeIntegrationException(String.format("Could not get process working directory. errno: %d", kernel32.GetLastError()));
            }
            return new File(Native.toString(out));
        }
    }

    public static class Unix implements ProcessEnvironment {
        final UnixLibC libc = (UnixLibC) Native.loadLibrary("c", UnixLibC.class);

        public void setenv(String name, String value) {
            if (value == null) {
                unsetenv(name);
                return;
            }
            int retval = libc.setenv(name, value, 1);
            if (retval != 0) {
                throw new NativeIntegrationException(String.format("Could not set environment variable '%s'. errno: %d", name, libc.errno()));
            }
        }

        public void unsetenv(String name) {
            int retval = libc.unsetenv(name);
            if (retval != 0) {
                throw new NativeIntegrationException(String.format("Could not unset environment variable '%s'. errno: %d", name, libc.errno()));
            }
        }

        public void setProcessDir(File dir) {
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
    }
}
