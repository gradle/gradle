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

class DisallowIncludingBuildSrcAsProjectIntegTest extends AbstractIntegrationSpec {

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


}
