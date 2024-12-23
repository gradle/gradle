/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.launcher.configuration;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.location.BuildLocationConfiguration;

import java.io.File;

/**
 * Immutable build layout parameters, calculated from the command-line options and environment.
 */
public interface BuildLocationResult {
    void applyTo(BuildLayoutParameters buildLayoutParameters);

    void applyTo(StartParameterInternal startParameter);

    BuildLocationConfiguration toLocationConfiguration();

    File getGradleInstallationHomeDir();

    File getGradleUserHomeDir();
}
