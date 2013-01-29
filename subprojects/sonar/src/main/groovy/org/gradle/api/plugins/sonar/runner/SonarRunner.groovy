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
package org.gradle.api.plugins.sonar.runner

import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils
import org.sonar.runner.Runner

/**
 * Analyses one or more projects with the <a href="">Sonar Runner</a>. Can
 * be used together with the {@code sonar-runner} plugin or standalone.
 * If used standalone, no defaults will be provided from Gradle's side.
 * The Sonar Runner can be configured via {@code sonarProperties} and/or
 * with {@code sonar-project.properties} and {@code sonar.properties}
 * files. For more information on how to configure the Sonar Runner,
 * and which properties are available, see the <a href="">Sonar documentation</a>.
 */
class SonarRunner extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(SonarRunner)

    /**
     * The directory where Sonar Runner places files necessary for its execution.
     */
    File bootstrapDir

    /**
     * The String key/value pairs to be passed to the Sonar Runner. {@code null} values are not permitted.
     */
    Properties sonarProperties

    @TaskAction
    void run() {
        GFileUtils.mkdirs(getBootstrapDir())
        def properties = getSonarProperties()
        if (LOGGER.infoEnabled) {
            LOGGER.info("Executing Sonar Runner with properties:\n{}",
                    properties.collect { key, value -> "$key: $value" }.join("\n"))
        }
        def runner = Runner.create(properties, getBootstrapDir())
        runner.execute()
    }
}
