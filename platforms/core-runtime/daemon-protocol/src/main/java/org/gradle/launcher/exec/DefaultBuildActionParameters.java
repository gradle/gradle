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
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.internal.GUtil;

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
    private final ClassPath injectedPluginClasspath;

    public DefaultBuildActionParameters(Map<?, ?> systemProperties, Map<String, String> envVariables, File currentDir, LogLevel logLevel, boolean useDaemon, ClassPath injectedPluginClasspath) {
        this.currentDir = currentDir;
        this.logLevel = logLevel;
        this.useDaemon = useDaemon;
        assert systemProperties != null;
        assert envVariables != null;

        this.systemProperties = new HashMap<String, String>();
        GUtil.addToMap(this.systemProperties, systemProperties);

        this.envVariables = new HashMap<String, String>(Cast.uncheckedCast(Jvm.getInheritableEnvironmentVariables(envVariables)));
        this.injectedPluginClasspath = injectedPluginClasspath;
    }

    @Override
    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    @Override
    public Map<String, String> getEnvVariables() {
        return envVariables;
    }

    @Override
    public File getCurrentDir() {
        return currentDir;
    }

    @Override
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
            + ", injectedPluginClasspath=" + injectedPluginClasspath
            + '}';
    }

    @Override
    public boolean isUseDaemon() {
        return useDaemon;
    }

    @Override
    public ClassPath getInjectedPluginClasspath() {
        return injectedPluginClasspath;
    }
}
