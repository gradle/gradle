/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class IncludedBuildTaskDependenciesIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/15875")
    void '#method can not reference tasks from another build'() {
        given:
        settingsFile << """
            includeBuild("producer")
            rootProject.name = "root"
        """
        file("producer").createDir()
        file("producer/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.producer"
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            tasks.register("demo") {
                $method gradle.includedBuild("producer").task(":build")
            }
        """

        expect:
        fails "demo"
        result.hasErrorOutput("Cannot use $method to reference tasks from another build")

        where:
        method << ["shouldRunAfter", "mustRunAfter"]
    }
}
