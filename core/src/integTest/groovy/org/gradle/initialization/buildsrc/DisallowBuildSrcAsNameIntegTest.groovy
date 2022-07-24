/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DisallowBuildSrcAsNameIntegTest extends AbstractIntegrationSpec {

    def "fails when trying to include buildSrc as project"() {
        buildFile << """
            task t
        """
        settingsFile << """
            include 'buildSrc'
        """

        when:
        fails "t"

        then:
        failure.assertHasDescription("'buildSrc' cannot be used as a project name as it is a reserved name")
    }

    def "fails when trying to use buildSrc as a project name"() {
        buildFile << """
            task t
        """
        settingsFile << """
            include 'b'
            project(":b").name = "buildSrc"
        """

        when:
        fails "t"

        then:
        failure.assertHasDescription("'buildSrc' cannot be used as a project name as it is a reserved name")
    }

    def "fails when trying to include buildSrc as project in an included build"() {
        settingsFile << """
            includeBuild 'i'
        """
        file("i/settings.gradle") << """
            include 'buildSrc'
        """
        buildFile << """
            task t
        """

        when:
        fails "t"

        then:
        failure.assertHasDescription("'buildSrc' cannot be used as a project name as it is a reserved name (in build :i)")
    }

    def "fails when trying to include a build with name buildSrc"() {
        def b = file("b")
         b.file("build.gradle") << ""
        buildFile << """
            task t
        """
        settingsFile << """
            includeBuild 'b', { name = 'buildSrc' }
        """

        when:
        fails "t"

        then:
        failure.assertHasDescription("Included build $b has build name 'buildSrc' which cannot be used as it is a reserved name.")
    }

    def "fails when trying to create a buildSrc build with GradleBuild task"() {
        def b = file("b")
        b.file("build.gradle") << ""
        buildFile << """
            task t(type: GradleBuild) {
                dir = "b"
                buildName = "buildSrc"
            }
        """

        when:
        fails "t"

        then:
        failure.assertHasDescription("Execution failed for task ':t'.")
        failure.assertHasCause("Included build $b has build name 'buildSrc' which cannot be used as it is a reserved name.")
    }

}
