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
import org.gradle.util.OperatingSystem;

import java.util.LinkedList;
import java.util.Map;

/**
 * Enables hacking the environment. *Not* thread safe.
 *
 * @author: Szczepan Faber, created at: 9/1/11
 */
public class LenientEnvHacker {

    private static final Logger LOGGER = Logging.getLogger(LenientEnvHacker.class);
    private Environment env;

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

    public void setenv(String key, String value) {
        if (env == null) {
            return;
        }
        try {
            env.setenv(key, value);
        } catch (Throwable t) {
            String warning = String.format("Unable to set env variable %s=%s on OS: %s.", key, value, OperatingSystem.current());
            LOGGER.warn(warning, t);
        }
    }

    //Existing system env is removed and passed env map becomes the system env.
    public void setenv(Map<String, String> source) {
        if (env == null) {
            return;
        }
        try {
            Map<String, String> currentEnv = System.getenv();
            Iterable<String> names = new LinkedList(currentEnv.keySet());
            for (String name : names) {
                env.unsetenv(name);
            }
            for (String key : source.keySet()) {
                env.setenv(key, source.get(key));
            }
        } catch (Throwable t) {
            String warning = String.format("Unable to set env variables on OS: %s.", OperatingSystem.current());
            LOGGER.warn(warning, t);
        }
    }

    public String getProcessDir() {
        if (env == null) {
            return null;
        }
        try {
            return env.getProcessDir();
        } catch (Throwable t) {
            String warning = String.format("Unable to get process dir on OS: %s.", OperatingSystem.current());
            LOGGER.warn(warning, t);
            return null;
        }
    }

    public void setProcessDir(String dirAbsolutePath) {
        if (env == null) {
            return;
        }
        try {
            env.setProcessDir(dirAbsolutePath);
        } catch (Throwable t) {
            String warning = String.format("Unable to set process dir to: %s on OS: %s.", dirAbsolutePath, OperatingSystem.current());
            LOGGER.warn(warning, t);
        }
    }

    static class EnvironmentProvider {
        public Environment getEnvironment() {
            return new Environment();
        }
    }
}
