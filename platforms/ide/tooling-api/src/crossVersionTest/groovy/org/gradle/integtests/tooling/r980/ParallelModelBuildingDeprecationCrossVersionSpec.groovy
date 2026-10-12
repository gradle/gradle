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

package org.gradle.integtests.tooling.r980

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/36001")
@TargetGradleVersion("current")
class ParallelModelBuildingDeprecationCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile "rootProject.name = 'root'"
    }

    def "deprecation is reported when parallel execution implicitly enables parallel model building with #args"() {
        when:
        expectImplicitParallelModelBuildingDeprecation()

        then:
        succeeds { connection ->
            connection.model(GradleProject).withArguments(["--parallel"] + args).get()
        }

        where:
        // The deprecation must fire regardless of ignore-legacy-default, because in Gradle 10 having
        // 'org.gradle.parallel' enabled without an explicit 'org.gradle.tooling.parallel' will be an error.
        args << [
            [],
            ["-Dorg.gradle.tooling.parallel.ignore-legacy-default=true"],
            ["-Dorg.gradle.tooling.parallel.ignore-legacy-default=false"],
        ]
    }

    def "no deprecation is reported when parallel model building is set explicitly with #args"() {
        expect:
        succeeds { connection ->
            connection.model(GradleProject).withArguments(["--parallel"] + args).get()
        }

        where:
        args << [
            ["-Dorg.gradle.tooling.parallel=true"],
            ["-Dorg.gradle.tooling.parallel=false"],
        ]
    }

    def "no deprecation is reported when parallel execution is not enabled"() {
        expect:
        succeeds { connection ->
            connection.model(GradleProject).get()
        }
    }

    def "no deprecation is reported when running tasks with parallel execution enabled"() {
        expect:
        withBuild { build ->
            build.forTasks("help").withArguments("--parallel")
        }
    }
}
