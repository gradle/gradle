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

package org.gradle.launcher.env;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.gradle.util.OperatingSystem;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Insipired on stuff I found on the web. Very draft now.
 *
 * @author: Szczepan Faber, created at: 9/1/11
 */
class Environment {

    public interface WinLibC extends Library {
        public int _putenv(String name);
    }
    public interface UnixLibC extends Library {
        public int setenv(String name, String value, int overwrite);
        public int unsetenv(String name);
    }

    static public class POSIX {
        static Object libc;
        static {
            if (OperatingSystem.current().isUnix()) {
                libc = Native.loadLibrary("c", UnixLibC.class);
            } else if (OperatingSystem.current().isWindows()) {
                libc = Native.loadLibrary("msvcrt", WinLibC.class);
            } else {
                throw new RuntimeException("We don't support this operating system: " + System.getProperty("os.name"));
            }
        }

        public int setenv(String name, String value, int overwrite) {
            if (libc instanceof UnixLibC) {
                return ((UnixLibC)libc).setenv(name, value, overwrite);
            }
            else {
                return ((WinLibC)libc)._putenv(name + "=" + value);
            }
        }

        public int unsetenv(String name) {
            if (libc instanceof UnixLibC) {
                return ((UnixLibC)libc).unsetenv(name);
            }
            else {
                return ((WinLibC)libc)._putenv(name + "=");
            }
        }
    }

    static POSIX libc = new POSIX();

    public static int unsetenv(String name) {
        Map<String, String> map = getenv();
        map.remove(name);
        Map<String, String> env2 = getwinenv();
        env2.remove(name);
        return libc.unsetenv(name);
    }

    public static int setenv(String name, String value, boolean overwrite) {
        if (name.lastIndexOf("=") != -1) {
            throw new IllegalArgumentException("Environment variable cannot contain '='");
        }
        Map<String, String> map = getenv();
        boolean contains = map.containsKey(name);
        if (!contains || overwrite) {
            map.put(name, value);
            Map<String, String> env2 = getwinenv();
            env2.put(name, value);
        }
        return libc.setenv(name, value, overwrite?1:0);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getwinenv() {
        try {
            Class<?> sc = Class.forName("java.lang.ProcessEnvironment");
            Field caseinsensitive = sc.getDeclaredField("theCaseInsensitiveEnvironment");
            caseinsensitive.setAccessible(true);
            return (Map<String, String>)caseinsensitive.get(null);
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to get mutable windows case insensitive environment map");
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getenv() {
        try {
            Map<String, String> theUnmodifiableEnvironment = System.getenv();
            Class<?> cu = theUnmodifiableEnvironment.getClass();
            Field m = cu.getDeclaredField("m");
            m.setAccessible(true);
            return (Map<String, String>)m.get(theUnmodifiableEnvironment);
        }
        catch (Exception ex2) {
            throw new RuntimeException("Unable to get mutable environment map");
        }
    }

}