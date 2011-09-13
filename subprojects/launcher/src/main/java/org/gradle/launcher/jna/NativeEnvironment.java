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

package org.gradle.launcher.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

/**
 * Uses jna to update the environment variables
 *
 * @author: Szczepan Faber, created at: 9/7/11
 */
public class NativeEnvironment {

    //CHECKSTYLE:OFF
    public interface WinLibC extends Library {
        public int _putenv(String name);
    }

    public interface UnixLibC extends Library {
        public int setenv(String name, String value, int overwrite);
        public int unsetenv(String name);
        public String getcwd(byte[] out, int size);
        public int chdir(String dirAbsolutePath);

    }
    //CHECKSTYLE:ON

    public static interface Posix {
        int setenv(String name, String value, int overwrite);
        int unsetenv(String name);
        void setProcessDir(String dir);
        String getProcessDir();
    }

    public static class Windows implements Posix {
        private final WinLibC libc = (WinLibC) Native.loadLibrary("msvcrt", WinLibC.class);

        public int setenv(String name, String value, int overwrite) {
            return libc._putenv(name + "=" + value);
        }

        public int unsetenv(String name) {
            return libc._putenv(name + "=");
        }

        public void setProcessDir(String dir) {
            Kernel32.INSTANCE.SetCurrentDirectoryW(new WString(dir));
        }

        public String getProcessDir() {
            int buf = 300;
            char[] out = new char[buf];
            Kernel32.INSTANCE.GetCurrentDirectory(buf, out);
            return "";
        }
    }

    public static class Unix implements Posix {
        final UnixLibC libc = (UnixLibC) Native.loadLibrary("c", UnixLibC.class);

        public int setenv(String name, String value, int overwrite) {
            return libc.setenv(name, value, overwrite);
        }

        public int unsetenv(String name) {
            return libc.unsetenv(name);
        }

        public void setProcessDir(String dir) {
            libc.chdir(dir);
        }

        public String getProcessDir() {
            byte[] out = new byte[1000];
            libc.getcwd(out, 1000);
            return Native.toString(out);
        }
    }
}
