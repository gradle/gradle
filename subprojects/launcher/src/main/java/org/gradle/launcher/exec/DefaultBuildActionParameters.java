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
package org.gradle.launcher.exec;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DefaultBuildActionParameters implements BuildActionParameters, Serializable {
    private final File currentDir;
    private final LogLevel logLevel;
    private final Map<String, String> systemProperties;
    private final Map<String, String> envVariables;

    private final boolean useDaemon;
    private final boolean continuous;
    private final boolean interactive;
    private final ClassPath injectedPluginClasspath;

    public DefaultBuildActionParameters(Map<?, ?> systemProperties, Map<String, String> envVariables, File currentDir, LogLevel logLevel, boolean useDaemon, boolean continuous, boolean interactive,
                                        ClassPath injectedPluginClasspath) {
        this.currentDir = currentDir;
        this.logLevel = logLevel;
        this.useDaemon = useDaemon;
        this.continuous = continuous;
        assert systemProperties != null;
        assert envVariables != null;
        this.systemProperties = new HashMap<String, String>();
        GUtil.addToMap(this.systemProperties, systemProperties);
        this.envVariables = new HashMap<String, String>(envVariables);
        this.interactive = interactive;
        this.injectedPluginClasspath = injectedPluginClasspath;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public Map<String, String> getEnvVariables() {
        return envVariables;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    @Override
    public String toString() {
        return "DefaultBuildActionParameters{"
            + ", currentDir=" + currentDir
            + ", systemProperties size=" + systemProperties.size()
            + ", envVariables size=" + envVariables.size()
            + ", logLevel=" + logLevel
            + ", useDaemon=" + useDaemon
            + ", continuous=" + continuous
            + ", interactive=" + interactive
            + ", injectedPluginClasspath=" + injectedPluginClasspath
            + '}';
    }

    public boolean isUseDaemon() {
        return useDaemon;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public ClassPath getInjectedPluginClasspath() {
        return injectedPluginClasspath;
    }
}
