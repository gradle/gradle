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

package org.gradle.launcher.daemon.configuration;

import org.gradle.StartParameter;

import java.io.File;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 1/22/13
 */
public class GradlePropertiesConfigurer {

    public GradleProperties configureStartParameter(StartParameter startParameter) {
        GradleProperties properties = this.prepareProperties(startParameter.getCurrentDir(), startParameter.isSearchUpwards(), startParameter.getGradleUserHomeDir(), startParameter.getMergedSystemProperties());
        properties.updateStartParameter(startParameter);
        return properties;
    }

    public GradleProperties prepareProperties(File projectDir, boolean searchUpwards, File gradleUserHomeDir, Map<?, ?> systemProperties) {
        return new GradleProperties()
            .configureFromBuildDir(projectDir, searchUpwards)
            .configureFromGradleUserHome(gradleUserHomeDir)
            .configureFromSystemProperties(systemProperties);
    }
}
