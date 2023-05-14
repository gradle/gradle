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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.execution.ProjectConfigurer
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.operations.BuildOperationExecutor
import spock.lang.Specification

class DefaultProjectsPreparerTest extends Specification {
    def startParameter = Mock(StartParameterInternal)
    def gradle = Mock(GradleInternal)
    def rootProject = Mock(ProjectInternal)
    def projectConfigurer = Mock(ProjectConfigurer)
    def modelParameters = Mock(BuildModelParameters)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def configurer = new DefaultProjectsPreparer(projectConfigurer, modelParameters, buildOperationExecutor)

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

    def "configures root build for on demand mode"() {
        when:
        configurer.prepareProjects(gradle)

        then:
        gradle.rootBuild >> true
        modelParameters.configureOnDemand >> true
    }

    def "configures non-root build for on demand mode"() {
        when:
        configurer.prepareProjects(gradle)

        then:
        gradle.rootBuild >> false
        modelParameters.configureOnDemand >> true
        1 * projectConfigurer.configureHierarchy(rootProject)
    }
}
