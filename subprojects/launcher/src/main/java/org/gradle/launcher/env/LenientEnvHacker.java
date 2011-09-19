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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.os.ProcessEnvironment;
import org.gradle.util.OperatingSystem;

import java.io.File;
import java.util.LinkedList;
import java.util.Map;

/**
 * Enables hacking the environment. *Not* thread safe.
 *
 * @author: Szczepan Faber, created at: 9/1/11
 */
public class LenientEnvHacker implements ProcessEnvironment {

    private static final Logger LOGGER = Logging.getLogger(LenientEnvHacker.class);
    private ProcessEnvironment env;

    public LenientEnvHacker() {
        this(new EnvironmentProvider());
    }

    public LenientEnvHacker(EnvironmentProvider provider) {
        try {
            this.env = provider.getEnvironment();
        } catch (Throwable t) {
            String warning = String.format("Unable to initialize native environment. Updating env variables will not be possible. Operation system: %s.", OperatingSystem.current());
            LOGGER.warn(warning, t);
            this.env = null;
        }
    }

    public void unsetenv(String name) {
        if (env == null) {
            return;
        }
        env.unsetenv(name);
    }

    public void setenv(String key, String value) {
        if (env == null) {
            return;
        }
        env.setenv(key, value);
    }

    //Existing system env is removed and passed env map becomes the system env.
    public void setenv(Map<String, String> source) {
        if (env == null) {
            return;
        }
        Map<String, String> currentEnv = System.getenv();
        Iterable<String> names = new LinkedList<String>(currentEnv.keySet());
        for (String name : names) {
            env.unsetenv(name);
        }
        for (String key : source.keySet()) {
            env.setenv(key, source.get(key));
        }
    }

    public File getProcessDir() {
        if (env == null) {
            return null;
        }
        return env.getProcessDir();
    }

    public void setProcessDir(File dir) {
        if (!dir.exists()) {
            return;
        }

        System.setProperty("user.dir", dir.getAbsolutePath());
        if (env == null) {
            return;
        }
        env.setProcessDir(dir);
    }

    static class EnvironmentProvider {
        public ProcessEnvironment getEnvironment() {
            return new Environment();
        }
    }
}
