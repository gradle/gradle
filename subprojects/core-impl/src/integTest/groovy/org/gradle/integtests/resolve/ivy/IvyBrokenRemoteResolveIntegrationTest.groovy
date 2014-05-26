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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

import static org.hamcrest.Matchers.containsString

class IvyBrokenRemoteResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    public void "reports and recovers from missing module"() {
        given:
        def repo = ivyHttpRepo("repo1")
        def module = repo.module("group", "projectA", "1.2").publish()

        buildFile << """
repositories {
    ivy { url "${repo.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing << { println configurations.missing.files }
"""

        when:
        module.ivy.expectGetMissing()
        module.jar.expectHeadMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
                .assertHasCause('Could not resolve all dependencies for configuration \':missing\'.')
                .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
    ${module.ivy.uri}
    ${module.jar.uri}
""")

        when:
        server.resetExpectations()
        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds('showMissing')
    }

    public void "reports and recovers from module missing from multiple repositories"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleInRepo1 = repo1.module("group", "projectA", "1.2").publish()
        def moduleInRepo2 = repo2.module("group", "projectA", "1.2")

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
        moduleInRepo1.ivy.expectGetMissing()
        moduleInRepo1.jar.expectHeadMissing()
        moduleInRepo2.ivy.expectGetMissing()
        moduleInRepo2.jar.expectHeadMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
                .assertHasCause('Could not resolve all dependencies for configuration \':missing\'.')
                .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
    ${moduleInRepo1.ivy.uri}
    ${moduleInRepo1.jar.uri}
    ${moduleInRepo2.ivy.uri}
    ${moduleInRepo2.jar.uri}
""")

        when:
        server.resetExpectations()
        moduleInRepo1.ivy.expectGet()
        moduleInRepo1.jar.expectGet()

        then:
        succeeds('showMissing')
    }

    public void "reports and recovers from failed Ivy descriptor download"() {
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
        module.ivy.expectGetBroken()
        fails("showBroken")

        then:
        failure
            .assertHasDescription('Execution failed for task \':showBroken\'.')
            .assertResolutionFailure(':broken')
            .assertHasCause('Could not resolve group:projectA:1.3.')
            .assertHasCause("Could not GET '${module.ivy.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds("showBroken")
    }

    public void "reports and caches missing artifacts"() {
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
        module.ivy.expectGet()
        module.jar.expectGetMissing()

        then:
        fails "retrieve"

        failure.assertThatCause(containsString("Artifact 'group:projectA:1.2:projectA.jar' not found"))

        when:
        server.resetExpectations()

        then:
        fails "retrieve"
        failure.assertThatCause(containsString("Artifact 'group:projectA:1.2:projectA.jar' not found"))
    }

    public void "reports and recovers from failed artifact download"() {
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
        module.ivy.expectGet()
        module.jar.expectGetBroken()

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download artifact 'group:projectA:1.2:projectA.jar'")
        failure.assertHasCause("Could not GET '${module.jar.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.jar.expectGet()

        then:
        succeeds "retrieve"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }
}
