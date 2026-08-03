/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language

import org.gradle.nativeplatform.fixtures.AvailableToolChains

abstract trait LanguageTaskNames {
    private static final String DEBUG = 'Debug'
    private static final String RELEASE = 'Release'

    abstract AvailableToolChains.InstalledToolChain getToolchainUnderTest()

    abstract String getLanguageTaskSuffix()

    String[] stripSymbolsTasks(String project = '', String buildType) {
        if (toolchainUnderTest.visualCpp) {
            return []
        } else {
            return ["${project}:stripSymbols${buildType}"]
        }
    }

    String getDebug() {
        DEBUG
    }

    String getRelease() {
        RELEASE
    }

    String getDebugShared() {
        return "${DEBUG}Shared"
    }

    /**
     * Returns the tasks for the project with the given path.
     */
    ProjectTasks tasks(String project) {
        return new ProjectTasks(project, toolchainUnderTest, languageTaskSuffix, additionalTestTaskNames)
    }

    /**
     * Returns the tasks for the root project.
     */
    ProjectTasks getTasks() {
        return new ProjectTasks('', toolchainUnderTest, languageTaskSuffix, additionalTestTaskNames)
    }

    String[] getAdditionalTestTaskNames() {
        return []
    }
}
