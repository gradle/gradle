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
import org.gradle.initialization.layout.BuildLayoutConfiguration;

import java.io.File;

/**
 * Immutable build layout parameters, calculated from the command-line options and environment.
 */
public interface BuildLayoutResult {
    void applyTo(BuildLayoutParameters buildLayout);

    void applyTo(StartParameterInternal startParameter);

    BuildLayoutConfiguration toLayoutConfiguration();

    File getGradleInstallationHomeDir();

    File getGradleUserHomeDir();
}
