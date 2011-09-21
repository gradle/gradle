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

import org.gradle.os.ProcessEnvironment;
import org.gradle.os.jna.NativeEnvironment;

import java.io.File;

/**
 * *Not* thread safe
 *
 * @author: Szczepan Faber, created at: 9/1/11
 */
class Environment implements ProcessEnvironment {

    //for updates to environment in a native way
    private final ProcessEnvironment nativeEnvironment;
    //for updates to private JDK caches of the environment state
    private final ReflectiveEnvironment reflectiveEnvironment = new ReflectiveEnvironment();

    Environment() {
        nativeEnvironment = NativeEnvironment.current();
    }

    public void removeEnvironmentVariable(String name) {
        reflectiveEnvironment.unsetenv(name);
        nativeEnvironment.removeEnvironmentVariable(name);
    }

    public void setEnvironmentVariable(String name, String value) {
        reflectiveEnvironment.setenv(name, value);
        nativeEnvironment.setEnvironmentVariable(name, value);
    }

    public File getProcessDir() {
        return nativeEnvironment.getProcessDir();
    }

    public void setProcessDir(File processDir) {
        nativeEnvironment.setProcessDir(processDir);
    }

    public Long getPid() {
        return nativeEnvironment.getPid();
    }
}