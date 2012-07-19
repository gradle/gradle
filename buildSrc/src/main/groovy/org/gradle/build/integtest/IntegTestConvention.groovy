/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build.integtest

import org.gradle.api.Project

class IntegTestConvention {
    private final Project project
    final List integTests = []

    IntegTestConvention(Project project) {
        this.project = project
    }

    String getIntegTestMode() {
        if (!project.tasks.findByName('ciBuild') || !project.gradle.taskGraph.populated) {
            return null
        }
        if (project.isCIBuild()) {
            return 'forking'
        }
        return System.getProperty("org.gradle.integtest.executer") ?: 'embedded'
    }

    File getIntegTestUserDir() {
        return project.file('intTestHomeDir')
    }

    File getIntegTestImageDir() {
        if (!project.tasks.findByName('intTestImage')) {
            return null
        }
        return project.intTestImage.destinationDir
    }
}