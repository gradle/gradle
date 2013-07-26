/*
 * Copyright 2013 the original author or authors.
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

class MavenBrokenRemoteResolveIntegrationTest extends AbstractDependencyResolutionTest {
    public void "reports POM that cannot be parsed"() {
        server.start()
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task showBroken << { println configurations.compile.files }
"""

        and:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        module.pomFile.text = "<project>"

        when:
        module.pom.expectGet()

        then:
        fails "showBroken"
        failure.assertResolutionFailure(":compile")
            .assertHasCause("Could not parse POM ${module.pom.uri}")
    }
}
