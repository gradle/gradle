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

package org.gradle.integtests.tooling.r15


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject

class ToolingApiConfigurationOnDemandCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        file("gradle.properties") << "org.gradle.configureondemand=true"
    }

    def "building model evaluates all projects regardless of configuration on demand mode"() {
        given:
        file("settings.gradle") << "include 'api', 'impl', 'other'"
        file("build.gradle") << """
            rootProject.description = 'Projects configured: '
            allprojects { afterEvaluate {
                rootProject.description += project.path + ", "
            }}
        """

        when:
        def op = withModel(GradleProject.class)

        then:
        op.model.description.contains 'Projects configured: :, :api, :impl, :other'
    }

    def "running tasks takes advantage of configuration on demand"() {
        given:
        file("settings.gradle") << "include 'api', 'impl', 'other'"

        file("build.gradle") << "allprojects { task foo }"
        file("impl/build.gradle") << "task bar(dependsOn: ':api:foo')"
        file("other/build.gradle") << "assert false: 'should not be evaluated'"

        when:
        withBuild { it.forTasks(":impl:bar") }

        then:
        noExceptionThrown()
    }
}
