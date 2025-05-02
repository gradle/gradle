/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher.cli.converter;

import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.initialization.BuildLayoutParametersBuildOptions;
import org.gradle.initialization.ParallelismBuildOptions;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class LayoutToPropertiesConverter {

    private final List<BuildOption<?>> allBuildOptions = new ArrayList<>();
    private final BuildLayoutFactory buildLayoutFactory;

    public LayoutToPropertiesConverter(BuildLayoutFactory buildLayoutFactory) {
        this.buildLayoutFactory = buildLayoutFactory;
        allBuildOptions.addAll(new BuildLayoutParametersBuildOptions().getAllOptions());
        allBuildOptions.addAll(new StartParameterBuildOptions().getAllOptions());
        allBuildOptions.addAll(new LoggingConfigurationBuildOptions().getAllOptions()); // TODO maybe a new converter also here
        allBuildOptions.addAll(new WelcomeMessageBuildOptions().getAllOptions()); // TODO maybe a new converter also here
        allBuildOptions.addAll(new DaemonBuildOptions().getAllOptions());
        allBuildOptions.addAll(new ParallelismBuildOptions().getAllOptions());
        allBuildOptions.addAll(new ToolchainBuildOptions().getAllOptions());
    }

    public AllProperties convert(InitialProperties initialProperties, BuildLayoutResult layout) {
        Map<String, String> properties = new HashMap<>();
        configureFromHomeDir(layout.getGradleInstallationHomeDir(), properties);
        configureFromBuildDir(layout, properties);
        configureFromHomeDir(layout.getGradleUserHomeDir(), properties);
        configureFromSystemPropertiesOfThisJvm(Cast.uncheckedNonnullCast(properties));
        properties.putAll(initialProperties.getRequestedSystemProperties());

        Map<String, String> daemonJvmProperties = new HashMap<>();
        configureFromDaemonJVMProperties(layout, daemonJvmProperties);
        return new Result(properties, daemonJvmProperties, initialProperties);
    }

    private void configureFromSystemPropertiesOfThisJvm(Map<Object, Object> properties) {
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key instanceof Serializable && (value instanceof Serializable || value == null)) {
                properties.put(key, value);
            }
        }
    }

    private void configureFromHomeDir(File gradleUserHomeDir, Map<String, String> result) {
        maybeConfigureFrom(new File(gradleUserHomeDir, Project.GRADLE_PROPERTIES), result);
    }

    private void configureFromBuildDir(BuildLayoutResult layoutResult, Map<String, String> result) {
        BuildLayout layout = buildLayoutFactory.getLayoutFor(layoutResult.toLayoutConfiguration());
        maybeConfigureFrom(new File(layout.getRootDirectory(), Project.GRADLE_PROPERTIES), result);
    }

    private void configureFromDaemonJVMProperties(BuildLayoutResult layoutResult, Map<String, String> result) {
        BuildLayout layout = buildLayoutFactory.getLayoutFor(layoutResult.toLayoutConfiguration());
        configureFrom(new File(layout.getRootDirectory(), DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE), result);
    }

    private void configureFrom(File propertiesFile, Map<String, String> result) {
        Properties properties = readProperties(propertiesFile);
        for (final Object key : properties.keySet()) {
            result.put(key.toString(), properties.get(key).toString());
        }
    }

    private void maybeConfigureFrom(File propertiesFile, Map<String, String> result) {
        Properties properties = readProperties(propertiesFile);
        for (final Object key : properties.keySet()) {
            String keyAsString = key.toString();
            BuildOption<?> validOption = CollectionUtils.findFirst(allBuildOptions,
                option -> keyAsString.equals(option.getProperty()) || keyAsString.equals(option.getDeprecatedProperty()));

            if (validOption != null) {
                result.put(key.toString(), properties.get(key).toString());
            }
        }
    }

    private static Properties readProperties(File propertiesFile) {
        Properties properties = new Properties();

        if (propertiesFile.isFile()) {

            try {
                try (FileInputStream inputStream = new FileInputStream(propertiesFile)) {
                    properties.load(inputStream);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return properties;
    }

    private static class Result implements AllProperties {
        private final Map<String, String> properties;
        private final Map<String, String> daemonJvmProperties;
        private final InitialProperties initialProperties;

        public Result(Map<String, String> properties, Map<String, String> daemonJvmProperties, InitialProperties initialProperties) {
            this.properties = properties;
            this.daemonJvmProperties = daemonJvmProperties;
            this.initialProperties = initialProperties;
        }

        @Override
        public Map<String, String> getRequestedSystemProperties() {
            return initialProperties.getRequestedSystemProperties();
        }

        @Override
        public Map<String, String> getProperties() {
            return Collections.unmodifiableMap(properties);
        }

        @Override
        public Map<String, String> getDaemonJvmProperties() {
            return Collections.unmodifiableMap(daemonJvmProperties);
        }

        @Override
        public Result merge(Map<String, String> systemProperties) {
            Map<String, String> properties = new HashMap<>(this.properties);
            properties.putAll(systemProperties);
            properties.putAll(initialProperties.getRequestedSystemProperties());
            return new Result(properties, daemonJvmProperties, initialProperties);
        }
    }
}
