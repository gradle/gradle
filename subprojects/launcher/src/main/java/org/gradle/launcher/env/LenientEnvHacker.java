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
    private final Environment env;

    public LenientEnvHacker() {
        this(new Environment());
    }

    public LenientEnvHacker(Environment env) {
        this.env = env;
    }

    public void setenv(String key, String value) {
        try {
            env.setenv(key, value, true);
        } catch (Throwable t) {
            String warning = String.format("Unable to set env variable %s=%s on OS: %s.", key, value, OperatingSystem.current());
            LOGGER.warn(warning, t);
        }
    }

    //Existing system env is removed and passed env map becomes the system env.
    public void setenv(Map<String, String> source) {
        try {
            Map<String, String> currentEnv = System.getenv();
            Iterable<String> names = new LinkedList(currentEnv.keySet());
            for (String name : names) {
                this.env.unsetenv(name);
            }
            for (String key : source.keySet()) {
                this.env.setenv(key, source.get(key), true);
            }
        } catch (Throwable t) {
            String warning = String.format("Unable to set env variables on OS: %s.", OperatingSystem.current());
            LOGGER.warn(warning, t);
        }
    }
}
