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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

import static org.hamcrest.Matchers.containsString

class IvyBrokenRemoteResolveIntegrationTest extends AbstractDependencyResolutionTest {

    public void "reports and caches missing module"() {
        server.start()

        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleInRepo1 = repo1.module("group", "projectA", "1.2")
        def moduleInRepo2 = repo2.module('group', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy { url "${repo1.uri}"}
    ivy { url "${repo2.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing << { println configurations.missing.files }
"""

        when:
        moduleInRepo1.expectIvyGetMissing()
        moduleInRepo1.expectJarHeadMissing()
        moduleInRepo2.expectIvyGet()
        moduleInRepo2.expectJarGet()

        then:
        succeeds("showMissing")

        when:
        server.resetExpectations() // Missing status in repo1 is cached
        then:
        succeeds('showMissing')
    }

    public void "reports and recovers from failed Ivy descriptor download"() {
        server.start()

        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    ivy {
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
        module.expectIvyGetBroken()
        fails("showBroken")

        then:
        failure
            .assertHasDescription('Execution failed for task \':showBroken\'.')
            .assertResolutionFailure(':broken')
            .assertHasCause('Could not resolve group:projectA:1.3.')
            .assertHasCause("Could not GET '${ivyHttpRepo.uri}/group/projectA/1.3/ivy-1.3.xml'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.expectIvyGet()
        module.expectJarGet()

        then:
        succeeds("showBroken")
    }

    public void "reports and caches missing artifacts"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
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
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.expectIvyGet()
        module.expectJarGetMissing()

        then:
        fails "retrieve"

        failure.assertThatCause(containsString("Artifact 'group:projectA:1.2@jar' not found"))

        when:
        server.resetExpectations()

        then:
        fails "retrieve"
        failure.assertThatCause(containsString("Artifact 'group:projectA:1.2@jar' not found"))
    }

    public void "reports and recovers from failed artifact download"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
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
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.expectIvyGet()
        module.expectJarGetBroken()

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download artifact 'group:projectA:1.2@jar'")
        failure.assertHasCause("Could not GET '${ivyHttpRepo.uri}/group/projectA/1.2/projectA-1.2.jar'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.expectJarGet()

        then:
        succeeds "retrieve"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    public void "reports Ivy descriptor that cannot be parsed"() {
        server.start()
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task showBroken << { println configurations.compile.files }
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        module.ivyFile.text = "<ivy-module>"

        when:
        module.expectIvyGet()

        then:
        fails "showBroken"
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivyFileUri}")
            .assertHasCause("invalid version null in ${module.ivyFileUri}")
    }
}
