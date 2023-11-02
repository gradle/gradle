/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling.r61

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaProject

@ToolingApiVersion('>=6.1')
@TargetGradleVersion(">=6.1")
class GradleProjectBuilderOptionsCrossVersionSpec extends ToolingApiSpecification {

    def "realizes #description when fetching GradleProject with option value #optionDescription"() {
        buildFile << """
            tasks.create("a") {
                println "realizing non-lazy task"
            }

            tasks.register("b") {
                println "realizing lazy task"
            }
        """

        when:
        def project = loadToolingModel(GradleProject) {
            if (option != null) {
                it.addJvmArguments("-Dorg.gradle.internal.GradleProjectBuilderOptions=$option")
            }
        }

        then:
        result.assertOutputContains("realizing non-lazy task")
        if (realizesLazy) {
            result.assertOutputContains("realizing lazy task")
        } else {
            result.assertNotOutput("realizing lazy task")
        }

        project.tasks.isEmpty() == !tasksInModel

        where:
        description      | option           | realizesLazy | tasksInModel
        "no lazy tasks"  | "omit_all_tasks" | false        | false
        "all lazy tasks" | "random_value"   | true         | true
        "all lazy tasks" | ""               | true         | true
        "all lazy tasks" | null             | true         | true

        optionDescription = describeOption(option)
    }

    def "realizes #description when fetching IdeaProject with option value #optionDescription"() {
        buildFile << """
            tasks.create("a") {
                println "realizing non-lazy task"
            }

            tasks.register("b") {
                println "realizing lazy task"
            }
        """

        when:
        def ideaProject = loadToolingModel(IdeaProject) {
            if (option != null) {
                it.addJvmArguments("-Dorg.gradle.internal.GradleProjectBuilderOptions=$option")
            }
        }

        then:
        result.assertOutputContains("realizing non-lazy task")
        if (!realizesLazy) {
            result.assertNotOutput("realizing lazy task")
        }

        ideaProject.children.size() == 1
        def project = ideaProject.children[0].gradleProject
        project.tasks.isEmpty() == !tasksInModel

        where:
        description      | option           | realizesLazy | tasksInModel
        "no lazy tasks"  | "omit_all_tasks" | false        | false
        "all lazy tasks" | "random_value"   | true         | true
        "all lazy tasks" | ""               | true         | true
        "all lazy tasks" | null             | true         | true

        optionDescription = describeOption(option)
    }

    private def describeOption(String s) {
        return s == null ? "is not set" : s.isEmpty() ? "is empty" : "'$s'"
    }

}
