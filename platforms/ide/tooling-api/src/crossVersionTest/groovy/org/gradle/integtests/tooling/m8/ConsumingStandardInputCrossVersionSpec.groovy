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

package org.gradle.integtests.tooling.m8

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import spock.lang.Timeout

class ConsumingStandardInputCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        if (!dist.toolingApiStdinInEmbeddedModeSupported) {
            // Did not work in embedded mode in older versions
            toolingApi.requireDaemons()
        }
    }

    @Timeout(90)
    def "consumes input when building model"() {
        given:
        file('build.gradle')  << """
description = System.in.text
"""
        when:
        GradleProject model = (GradleProject) withConnection { ProjectConnection connection ->
            def model = connection.model(GradleProject.class)
            model.standardInput = new ByteArrayInputStream("Cool project".bytes)
            model.get()
        }

        then:
        model.description == 'Cool project'
    }

    @Timeout(90)
    def "works well if the standard input configured with null"() {
        given:
        file('build.gradle')  << """
description = System.in.text
"""
        when:
        GradleProject model = (GradleProject) withConnection { ProjectConnection connection ->
            def model = connection.model(GradleProject.class)
            model.standardInput = null
            model.get()
        }

        then:
        model.description == ""
    }

    @Timeout(90)
    def "does not consume input when not explicitly provided"() {
        given:
        file('build.gradle')  << """
description = "empty" + System.in.text
"""
        when:
        GradleProject model = (GradleProject) withConnection { ProjectConnection connection ->
            def model = connection.model(GradleProject.class)
            model.get()
        }

        then:
        model.description == 'empty'
    }

    @Timeout(90)
    def "consumes input when running tasks"() {
        given:
        file('build.gradle') << """
task createFile {
    doLast {
        file('input.txt') << System.in.text
    }
}
"""
        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardInput = new ByteArrayInputStream("Hello world!".bytes)
            build.forTasks('createFile')
            build.run()
        }

        then:
        file('input.txt').text == "Hello world!"
    }
}
