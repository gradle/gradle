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
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.launcher.daemon.configuration.GradleProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class LayoutToPropertiesConverter {
    public Map<String, String> convert(BuildLayoutParameters layout, Map<String, String> properties) {
        configureFromBuildDir(layout.getProjectDir(), layout.getSearchUpwards(), properties);
        configureFromGradleUserHome(layout.getGradleUserHomeDir(), properties);
        properties.putAll((Map) System.getProperties());
        return properties;
    }

    private void configureFromGradleUserHome(File gradleUserHomeDir, Map<String, String> result) {
        maybeConfigureFrom(new File(gradleUserHomeDir, Project.GRADLE_PROPERTIES), result);
    }

    private void configureFromBuildDir(File currentDir, boolean searchUpwards, Map<String, String> result) {
        BuildLayoutFactory factory = new BuildLayoutFactory();
        BuildLayout layout = factory.getLayoutFor(currentDir, searchUpwards);
        maybeConfigureFrom(new File(layout.getRootDirectory(), Project.GRADLE_PROPERTIES), result);
    }

    private void maybeConfigureFrom(File propertiesFile, Map<String, String> result) {
        if (!propertiesFile.isFile()) {
            return;
        }

        Properties properties = new Properties();
        try {
            FileInputStream inputStream = new FileInputStream(propertiesFile);
            try {
                properties.load(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (Object key : properties.keySet()) {
            if (GradleProperties.ALL.contains(key.toString())) {
                result.put(key.toString(), properties.get(key).toString());
            }
        }
    }
}
