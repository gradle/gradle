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

import org.gradle.launcher.jna.NativeEnvironment;
import org.gradle.util.OperatingSystem;

/**
 * *Not* thread safe
 *
 * @author: Szczepan Faber, created at: 9/1/11
 */
class Environment {

    //for updates to environment in a native way
    private final NativeEnvironment.Posix posix;
    //for updates to private JDK caches of the environment state
    private final ReflectiveEnvironment reflectiveEnvironment = new ReflectiveEnvironment();

    Environment() {
        if (OperatingSystem.current().isUnix()) {
            posix = new NativeEnvironment.Unix();
        } else if (OperatingSystem.current().isWindows()) {
            posix = new NativeEnvironment.Windows();
        } else {
            throw new RuntimeException("We don't support this operating system: " + OperatingSystem.current());
        }
    }

    public int unsetenv(String name) {
        reflectiveEnvironment.unsetenv(name);
        return posix.unsetenv(name);
    }

    public int setenv(String name, String value) {
        reflectiveEnvironment.setenv(name, value);
        return posix.setenv(name, value, 1);
    }
}