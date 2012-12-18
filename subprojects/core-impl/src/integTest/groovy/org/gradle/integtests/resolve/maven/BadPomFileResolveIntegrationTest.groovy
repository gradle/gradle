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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import spock.lang.Issue

class BadPomFileResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("http://issues.gradle.org/browse/GRADLE-1005")
    def "can handle self referencing dependency"() {
        given:
        file("settings.gradle") << "include 'client'"

        and:
        mavenRepo().module('group', 'artifact', '1.0').dependsOn('group', 'artifact', '1.0').publish()

        and:
        buildFile << """
            repositories {
                maven { url "${mavenRepo().uri}" }
            }
            configurations { compile }
            dependencies {
                compile "group:artifact:1.0"
            }
            task libs << { assert configurations.compile.files.collect {it.name} == ['artifact-1.0.jar'] }
        """

        expect:
        succeeds ":libs"
    }
}