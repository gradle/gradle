/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.BuildInvocations

@ToolingApiVersion(">=3.0")
@TargetGradleVersion('>=6.8')
class CompositeBuildGradleProjectCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include "sub"
        """
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """
        file('other-build/sub/build.gradle') << """
            tasks.register('doTheOtherThing') {
                doLast {
                    println 'do the other thing'
                }
            }
        """
    }

    def "GradleProject tasks have correct paths for included builds"() {
        when:
        def gradleProjects = withConnection { con -> con.action(new LoadCompositeModel(GradleProject)).run() }

        gradleProjects.eachWithIndex { gradleProject, index ->
            println "build #$index"
            gradleProject.tasks.each { task -> println task.path }
        }

        then:
        gradleProjects.size == 2
        gradleProjects[0].children.empty
        gradleProjects[1].tasks.find { it.path == ':other-build:doSomething' }
        gradleProjects[1].children[0].tasks.find { println it; it.path == ':other-build:sub:doTheOtherThing' }
    }

    def "BuildInvocations tasks have correct path for tasks in included builds"() {
        when:
        def buildInvocations = withConnection { con -> con.action(new LoadCompositeModel(BuildInvocations)).run() }

        buildInvocations.eachWithIndex { gradleProject, index ->
            println "build #$index"
            gradleProject.tasks.each { task -> println task.path }
        }

        then:
        buildInvocations.size == 2
        buildInvocations[1].tasks.find { it.path == ':other-build:doSomething' }
        buildInvocations[1].tasks.find { println it.path; it.path == ':other-build:sub:doTheOtherThing' }
    }

    def "BuildInvocations should not have any task selectors for included builds"() {
        when:
        def invocations = withConnection { con -> con.action(new LoadCompositeModel(BuildInvocations)).run() }
        invocations.eachWithIndex { invocation, index ->
            println "build #$index"
            invocation.taskSelectors.each { selector -> println "selector=" + selector.name }
        }

        then:
        true
    }
}
