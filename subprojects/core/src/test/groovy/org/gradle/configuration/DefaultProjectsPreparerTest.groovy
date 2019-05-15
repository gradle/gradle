/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.execution.ProjectConfigurer
import org.gradle.initialization.BuildLoader
import org.gradle.initialization.ModelConfigurationListener
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.operations.BuildOperationExecutor
import spock.lang.Specification

class DefaultProjectsPreparerTest extends Specification {
    def startParameter = Mock(StartParameter)
    def gradle = Mock(GradleInternal)
    def rootProject = Mock(ProjectInternal)
    def projectConfigurer = Mock(ProjectConfigurer)
    def buildRegistry = Mock(BuildStateRegistry)
    def buildLoader = Mock(BuildLoader)
    def modelListener = Mock(ModelConfigurationListener)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    private configurer = new DefaultProjectsPreparer(projectConfigurer, buildRegistry, buildLoader, modelListener, buildOperationExecutor)

    def setup() {
        gradle.startParameter >> startParameter
        gradle.rootProject >> rootProject
    }

    def "configures build for standard mode"() {
        when:
        configurer.prepareProjects(gradle)

        then:
        1 * projectConfigurer.configureHierarchy(rootProject)
    }

    def "configures build for on demand mode"() {
        when:
        configurer.prepareProjects(gradle)

        then:
        startParameter.isConfigureOnDemand() >> true
        1 * projectConfigurer.configure(rootProject)
    }
}
