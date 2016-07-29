/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r30

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=3.0')
@TargetGradleVersion('>=3.0')
class ToolingApiEclipseModelRetrievalCrossVersionTest extends ToolingApiSpecification {

    def "Whether or not the eclipse plugin is explicitly applied, the same model is retrieved "() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile << """
            apply plugin: 'java'
            ${eclipsePluginApplied ? "apply plugin: 'eclipse'" : ""}
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        // EclipsePlugin.configureEclipseClasspath() registers the JRE container in an afterEvaluate block
        !project.classpathContainers.isEmpty()

        where:
        eclipsePluginApplied << [false, true]
    }
}
