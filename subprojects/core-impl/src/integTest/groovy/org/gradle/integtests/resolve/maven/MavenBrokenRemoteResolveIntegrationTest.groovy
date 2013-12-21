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
    public void "reports and recovers from failed POM download"() {
        server.start()

        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    maven {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { broken }
dependencies {
    broken 'group:projectA:1.3'
}
task showBroken << { println configurations.broken.files }
"""

        when:
        module.pom.expectGetBroken()
        fails("showBroken")

        then:
        failure
            .assertHasDescription('Execution failed for task \':showBroken\'.')
            .assertResolutionFailure(':broken')
            .assertHasCause('Could not resolve group:projectA:1.3.')
            .assertHasCause("Could not GET '${module.pom.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds("showBroken")
    }

    public void "reports and recovers from failed artifact download"() {
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
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.pom.expectGet()
        module.artifact.expectGetBroken()

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download artifact 'group:projectA:1.2:projectA.jar'")
        failure.assertHasCause("Could not GET '${module.artifact.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.artifact.expectGet()

        then:
        succeeds "retrieve"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }
}
