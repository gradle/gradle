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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.ivy.IvyFileModule

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith

class IvyBrokenRemoteResolveIntegrationTest extends AbstractDependencyResolutionTest {

    public void "reports and caches missing module"() {
        server.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo1"}
    ivy { url "http://localhost:${server.port}/repo2"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
if (project.hasProperty('doNotCacheChangingModules')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
task showMissing << { println configurations.missing.files }
"""

        when:
        expectMissingArtifact("repo1", module)
        expectExistingArtifact("repo2", module)

        then:
        succeeds("showMissing")

        when:
        server.resetExpectations() // Missing status in repo1 is cached
        then:
        succeeds('showMissing')
    }

    public void "reports and recovers from broken module"() {
        server.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.3')
        module.publish()

        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}
configurations { broken }
dependencies {
    broken 'group:projectA:1.3'
}
task showBroken << { println configurations.broken.files }
"""

        when:
        server.addBroken('/')
        fails("showBroken")

        then:
        failure.assertHasDescription('Execution failed for task \':showBroken\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':broken\'.')
        failure.assertHasCause('Could not resolve group:group, module:projectA, version:1.3')
        failure.assertHasCause("Could not GET 'http://localhost:${server.port}/group/projectA/1.3/ivy-1.3.xml'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        server.expectGet('/group/projectA/1.3/ivy-1.3.xml', module.ivyFile)
        server.expectGet('/group/projectA/1.3/projectA-1.3.jar', module.jarFile)

        then:
        succeeds("showBroken")
    }

    public void "reports and caches missing artifacts"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
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
        def module = ivyRepo().module('group', 'projectA', '1.2')
        module.publish()

        when:
        server.expectGet('/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGetMissing('/group/projectA/1.2/projectA-1.2.jar')

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
        url "http://localhost:${server.port}"
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
        def module = ivyRepo().module('group', 'projectA', '1.2')
        module.publish()

        when:
        server.expectGet('/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.addBroken('/group/projectA/1.2/projectA-1.2.jar')

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download artifact 'group:projectA:1.2@jar'")
        failure.assertThatCause(startsWith("Could not GET"))

        when:
        server.resetExpectations()
        server.expectGet('/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds "retrieve"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def expectExistingArtifact(String repo, IvyFileModule module) {
        server.expectGet("/${repo}/${module.getOrganisation()}/${module.getModule()}/${module.getRevision()}/ivy-${module.getRevision()}.xml", module.ivyFile)
        server.expectGet("/${repo}/${module.getOrganisation()}/${module.getModule()}/${module.getRevision()}/${module.getModule()}-${module.getRevision()}.jar", module.jarFile)
    }

    def expectMissingArtifact(String repo, IvyFileModule module) {
        server.expectGetMissing("/${repo}/${module.getOrganisation()}/${module.getModule()}/${module.getRevision()}/ivy-${module.getRevision()}.xml")
        server.expectHeadMissing("/${repo}/${module.getOrganisation()}/${module.getModule()}/${module.getRevision()}/${module.getModule()}-${module.getRevision()}.jar")
    }
}
