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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Issue

class MavenVersionRangeResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("GRADLE-3334")
    def "can resolve version range with single value specified"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile group: "org.test", name: "projectA", version: "[1.1]"
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        mavenRepo.module('org.test', 'projectB', '2.0').publish()
        mavenRepo.module('org.test', 'projectA', '1.1').dependsOn('org.test', 'projectB', '[2.0]').publish()

        when:
        succeeds 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-2.0.jar')
    }
}
