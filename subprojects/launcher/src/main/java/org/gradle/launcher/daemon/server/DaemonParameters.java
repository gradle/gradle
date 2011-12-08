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
package org.gradle.launcher.daemon.server;

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.List;
import java.util.Map;

public class DaemonParameters {
    public static final String IDLE_TIMEOUT_SYS_PROPERTY = "org.gradle.daemon.idletimeout";
    public static final String SYSTEM_PROPERTY_KEY = "org.gradle.daemon.registry.base";
    static final int DEFAULT_IDLE_TIMEOUT = 3 * 60 * 60 * 1000;
    private File baseDir = new File(StartParameter.DEFAULT_GRADLE_USER_HOME, "daemon");
    private List<String> jvmArgs;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT;

    public File getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(File baseDir) {
        this.baseDir = GFileUtils.canonicalise(baseDir);
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public void setJvmArgs(List<String> jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public void useGradleUserHomeDir(File gradleUserHomeDir) {
        setBaseDir(new File(gradleUserHomeDir, "daemon"));
    }

    public void configureFromSystemProperties(Map<String, String> properties) {
        String propertyValue = properties.get(IDLE_TIMEOUT_SYS_PROPERTY);
        if (propertyValue != null) {
            try {
                idleTimeout = Integer.parseInt(propertyValue);
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s sys property. The value should be an int but is: %s", IDLE_TIMEOUT_SYS_PROPERTY, propertyValue));
            }
        }
        propertyValue = properties.get(SYSTEM_PROPERTY_KEY);
        if (propertyValue != null) {
            try {
                setBaseDir(new File(propertyValue));
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s sys property. The value should be an int but is: %s", IDLE_TIMEOUT_SYS_PROPERTY, propertyValue));
            }
        }
    }
}
