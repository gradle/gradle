/*
 * Copyright 2017 the original author or authors.
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


class BuildSrcIntegrationTest extends AbstractIntegrationSpec {
    def "includes build identifier in error message on failure to resolve dependencies of buildSrc build"() {
        given:
        def buildSrc = file("buildSrc/build.gradle")
        buildSrc << """
            repositories {
                maven { url '$mavenRepo.uri' }
            }

            dependencies {
                implementation "org.test:test:1.2"
            }
        """
        file("buildSrc/src/main/java/Thing.java") << "class Thing { }"

        when:
        fails()

        then:
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':buildSrc:runtimeClasspath'.")
        failure.assertHasCause("Could not find org.test:test:1.2.")

        when:
        def m = mavenRepo.module("org.test", "test", "1.2").publish()
        m.artifact.file.delete()

        fails()

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':buildSrc:runtimeClasspath'.")
        failure.assertHasCause("Could not find test.jar (org.test:test:1.2).")
    }

}
