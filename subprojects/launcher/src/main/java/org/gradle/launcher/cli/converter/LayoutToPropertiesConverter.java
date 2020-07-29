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
import org.gradle.api.specs.Spec;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.BuildLayoutParametersBuildOptions;
import org.gradle.initialization.ParallelismBuildOptions;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
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
        allBuildOptions.addAll(new LoggingConfigurationBuildOptions().getAllOptions());
        allBuildOptions.addAll(new DaemonBuildOptions().getAllOptions());
        allBuildOptions.addAll(new ParallelismBuildOptions().getAllOptions());
    }

    public AllProperties convert(InitialProperties initialProperties, BuildLayoutResult layout) {
        BuildLayoutParameters layoutParameters = new BuildLayoutParameters();
        layout.applyTo(layoutParameters);
        Map<String, String> properties = new HashMap<>();
        configureFromHomeDir(layoutParameters.getGradleInstallationHomeDir(), properties);
        configureFromBuildDir(layoutParameters.getSearchDir(), layoutParameters.getSearchUpwards(), properties);
        configureFromHomeDir(layout.getGradleUserHomeDir(), properties);
        configureFromSystemPropertiesOfThisJvm(Cast.uncheckedNonnullCast(properties));
        properties.putAll(initialProperties.getRequestedSystemProperties());
        return new Result(properties, initialProperties);
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

    private void configureFromBuildDir(File currentDir, boolean searchUpwards, Map<String, String> result) {
        BuildLayout layout = buildLayoutFactory.getLayoutFor(currentDir, searchUpwards);
        maybeConfigureFrom(new File(layout.getRootDirectory(), Project.GRADLE_PROPERTIES), result);
    }

    private void maybeConfigureFrom(@Nullable File propertiesFile, Map<String, String> result) {
        if (propertiesFile != null && !propertiesFile.isFile()) {
            return;
        }

        Properties properties = new Properties();
        try {
            try (FileInputStream inputStream = new FileInputStream(propertiesFile)) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (final Object key : properties.keySet()) {
            BuildOption<?> validOption = CollectionUtils.findFirst(allBuildOptions, new Spec<BuildOption<?>>() {
                @Override
                public boolean isSatisfiedBy(BuildOption<?> option) {
                    return option.getGradleProperty() != null ? option.getGradleProperty().equals(key.toString()) : false;
                }
            });

            if (validOption != null) {
                result.put(key.toString(), properties.get(key).toString());
            }
        }
    }

    private static class Result implements AllProperties {
        private final Map<String, String> properties;
        private final InitialProperties initialProperties;

        public Result(Map<String, String> properties, InitialProperties initialProperties) {
            this.properties = properties;
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
        public Result merge(Map<String, String> systemProperties) {
            Map<String, String> properties = new HashMap<>(this.properties);
            properties.putAll(systemProperties);
            properties.putAll(initialProperties.getRequestedSystemProperties());
            return new Result(properties, initialProperties);
        }
    }
}
