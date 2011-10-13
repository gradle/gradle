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

import org.gradle.api.internal.Factory;
import org.gradle.os.ProcessEnvironment;
import org.gradle.os.jna.NativeEnvironment;

import java.io.File;
import java.util.LinkedList;
import java.util.Map;

/**
 * Enables hacking the environment. *Not* thread safe.
 *
 * @author: Szczepan Faber, created at: 9/1/11
 */
public class LenientEnvHacker implements ProcessEnvironment {

    private ProcessEnvironment env;

    public LenientEnvHacker() {
        this(new EnvironmentProvider());
    }

    public LenientEnvHacker(Factory<? extends ProcessEnvironment> provider) {
        this.env = provider.create();
    }

    public void removeEnvironmentVariable(String name) {
        env.removeEnvironmentVariable(name);
    }

    public boolean maybeRemoveEnvironmentVariable(String name) {
        return env.maybeRemoveEnvironmentVariable(name);
    }

    public void setEnvironmentVariable(String key, String value) {
        env.setEnvironmentVariable(key, value);
    }

    public boolean maybeSetEnvironmentVariable(String name, String value) {
        return env.maybeSetEnvironmentVariable(name, value);
    }

    //Existing system env is removed and passed env map becomes the system env.
    public void setenv(Map<String, String> source) {
        Map<String, String> currentEnv = System.getenv();
        Iterable<String> names = new LinkedList<String>(currentEnv.keySet());
        for (String name : names) {
            env.maybeRemoveEnvironmentVariable(name);
        }
        for (String key : source.keySet()) {
            env.maybeSetEnvironmentVariable(key, source.get(key));
        }
    }

    public File getProcessDir() {
        return env.getProcessDir();
    }

    public void setProcessDir(File dir) {
        env.setProcessDir(dir);
    }

    public boolean maybeSetProcessDir(File processDir) {
        return env.maybeSetProcessDir(processDir);
    }

    public Long getPid() {
        return env.getPid();
    }

    public Long maybeGetPid() {
        return env.maybeGetPid();
    }

    static class EnvironmentProvider implements Factory<ProcessEnvironment> {
        public ProcessEnvironment create() {
            return NativeEnvironment.current();
        }
    }
}
