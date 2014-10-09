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
package org.gradle.api.plugins.quality

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.resources.TextResource

class CheckstyleExtension extends CodeQualityExtension {
    private final Project prj

    CheckstyleExtension(Project project) {
        prj = project
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Incubating
    TextResource config

    /**
     * The Checkstyle configuration file to use.
     */
    File getConfigFile() {
        getConfig().asFile()
    }

    /**
     * The Checkstyle configuration file to use.
     */
    void setConfigFile(File configFile) {
        setConfig(prj.resources.text.fromFile(configFile))
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration
     * file.
     */
    Map<String, Object> configProperties = [:]

    /**
     * Whether or not rule violations are to be displayed on the console. Defaults to <tt>true</tt>.
     *
     * Example: showViolations = false
     */
    boolean showViolations = true
}
