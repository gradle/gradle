/*
 * Copyright 2015 the original author or authors.
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




package org.gradle.integtests.tooling.r24

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.build.BuildEnvironment

class BuildEnvironmentCrossVersionSpec extends ToolingApiSpecification {

    def "provide Gradle user home information on BuildEnvironment"() {
        file("build.gradle")

        when:
        def buildEnvironment = withConnection { ProjectConnection connection ->
            connection.getModel(BuildEnvironment.class)
        }

        then:
        buildEnvironment != null
        buildEnvironment.gradle.gradleUserHome != null
        buildEnvironment.gradle.gradleUserHome == toolingApi.gradleUserHomeDir
    }

}
