/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.api.NonNullApi;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.wrapper.GradleUserHomeLookup;
import org.gradle.wrapper.PropertiesFileHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains convenience methods to read some Gradle configuration without launching a daemon.
 */
@NonNullApi
public class ConnectionConfigurationUtil {

    public static Map<String, String> determineSystemProperties(ConnectionParameters connectionParameters) {
        Map<String, String> systemProperties = new HashMap<>();
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            systemProperties.put(entry.getKey().toString(), entry.getValue() == null ? null : entry.getValue().toString());
        }
        systemProperties.putAll(PropertiesFileHandler.getSystemProperties(new File(determineRootDir(connectionParameters), "gradle.properties")));
        systemProperties.putAll(PropertiesFileHandler.getSystemProperties(new File(determineRealUserHomeDir(connectionParameters), "gradle.properties")));
        return systemProperties;
    }

    public static List<String> determineJvmArguments(ConnectionParameters connectionParameters) {
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.addAll(PropertiesFileHandler.getJvmArgs(new File(determineRootDir(connectionParameters), "gradle.properties")));
        jvmArgs.addAll(PropertiesFileHandler.getJvmArgs(new File(determineRealUserHomeDir(connectionParameters), "gradle.properties")));
        return jvmArgs;
    }

    public static File determineRootDir(ConnectionParameters connectionParameters) {
        return new BuildLayoutFactory().getLayoutFor(
            connectionParameters.getProjectDir(),
            connectionParameters.isSearchUpwards() != null ? connectionParameters.isSearchUpwards() : true
        ).getRootDirectory();
    }

    public static File determineRealUserHomeDir(ConnectionParameters connectionParameters) {
        File distributionBaseDir = connectionParameters.getDistributionBaseDir();
        if (distributionBaseDir != null) {
            return distributionBaseDir;
        }
        File userHomeDir = connectionParameters.getGradleUserHomeDir();
        return userHomeDir != null ? userHomeDir : GradleUserHomeLookup.gradleUserHome();
    }
}
