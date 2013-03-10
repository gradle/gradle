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
import spock.lang.Specification

class DefaultBuildConfigurerTest extends Specification {
    private startParameter = Mock(StartParameter)
    private gradle = Mock(GradleInternal)
    private rootProject = Mock(ProjectInternal)
    private configurer = new DefaultBuildConfigurer()

    def setup() {
        gradle.startParameter >> startParameter
        gradle.rootProject >> rootProject
    }

    def "configures build for standard mode"() {
        def child1 = Mock(ProjectInternal)
        def child2 = Mock(ProjectInternal)

        given:
        _ * rootProject.allprojects >> [rootProject, child1, child2]

        when:
        configurer.configure(gradle)

        then:
        1 * gradle.addProjectEvaluationListener(_ as ProjectDependencies2TaskResolver);
        1 * rootProject.evaluate()
        1 * child1.evaluate()
        1 * child2.evaluate()
    }

    def "configures build for on demand mode"() {
        when:
        configurer.configure(gradle)

        then:
        1 * startParameter.isConfigureOnDemand() >> true
        1 * rootProject.evaluate()
        0 * rootProject._

        and:
        1 * gradle.addProjectEvaluationListener(_ as ProjectDependencies2TaskResolver);
    }
}
