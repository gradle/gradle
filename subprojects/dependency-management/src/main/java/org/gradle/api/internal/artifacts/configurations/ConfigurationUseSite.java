/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.util.Path;

import javax.annotation.Nullable;

final class ConfigurationUseSite {

    private static final ConfigurationUseSite SCRIPT = new ConfigurationUseSite(null, true);
    private static final ConfigurationUseSite UNKNOWN = new ConfigurationUseSite(null, false);

    private final Path projectPath;
    private final boolean isScript;

    private ConfigurationUseSite(Path projectPath, boolean isScript) {
        this.projectPath = projectPath;
        this.isScript = isScript;
    }

    /**
     * The path to the project that owns this configuration, if owned by a project.
     */
    @Nullable
    public Path getProjectPath() {
        return projectPath;
    }

    /**
     * Whether the configuration is part of a scripts set (e.g. buildscript.configurations)
     */
    public boolean isScript() {
        return isScript;
    }

    public static ConfigurationUseSite script() {
        return SCRIPT;
    }

    public static ConfigurationUseSite unknown() {
        return UNKNOWN;
    }

    public static ConfigurationUseSite project(Path projectPath) {
        return new ConfigurationUseSite(projectPath, false);
    }

}
