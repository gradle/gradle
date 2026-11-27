/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r1000

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject

@TargetGradleVersion(">=10.0")
class ToolingApiIdeaModelProjectDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def "subtracting the runtime classpath from the provided scope leaves a project dependency out of compile scope"() {
        given:
        includeProjects("a", "b")
        file("a/build.gradle") << """
            plugins {
                id 'java-library'
                id 'idea'
            }
            dependencies {
                implementation project(':b')
            }
            idea.module.scopes.PROVIDED.minus += [configurations.runtimeClasspath]
        """
        file("b/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """

        when:
        def a = loadToolingModel(IdeaProject).modules.find { it.name == "a" }
        def dependencies = a.dependencies.findAll { it instanceof IdeaModuleDependency }

        then:
        dependencies*.targetModuleName.unique() == ["b"]
        dependencies*.scope*.scope as Set == ["RUNTIME", "TEST"] as Set
    }
}
