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

import org.gradle.integtests.fixtures.IvyModule
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import spock.lang.Ignore

class IvyDynamicRevisionRemoteResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "uses latest version from version range and latest status"() {
        server.start()
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}

configurations { compile }

dependencies {
    compile group: "group", name: "projectA", version: "1.+"
    compile group: "group", name: "projectB", version: "latest.integration"
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor 0, "seconds"
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when: "Version 1.1 is published"
        def projectA1 = repo.module("group", "projectA", "1.1").publish()
        repo.module("group", "projectA", "2.0").publish()
        def projectB1 = repo.module("group", "projectB", "1.1").publish()

        and: "Server handles requests"
        serveUpDynamicRevision(projectA1)
        serveUpDynamicRevision(projectB1)

        and:
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.1.jar').assertIsCopyOf(projectB1.jarFile)

        when: "New versions are published"
        def projectA2 = repo.module("group", "projectA", "1.2").publish()
        def projectB2 = repo.module("group", "projectB", "2.2").publish()

        and: "Server handles requests"
        server.resetExpectations()
        serveUpDynamicRevision(projectA2)
        serveUpDynamicRevision(projectB2)

        and:
        run 'retrieve'

        then: "New versions are used"
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-2.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(projectA2.jarFile)
        file('libs/projectB-2.2.jar').assertIsCopyOf(projectB2.jarFile)
    }


    def "determines latest version with jar only"() {
        server.start()
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
  ivy {
      url "http://localhost:${server.port}"
  }
}

configurations { compile }

dependencies {
  compile group: "group", name: "projectA", version: "1.+"
}

task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""

        when: "Version 1.1 is published"
        def projectA11 = repo.module("group", "projectA", "1.1").publish()
        def projectA12 = repo.module("group", "projectA", "1.2").publish()
        repo.module("group", "projectA", "2.0").publish()

        and: "Server handles requests"
        server.expectGetDirectoryListing("/${projectA12.organisation}/${projectA12.module}/", projectA12.moduleDir.parentFile)
        server.expectGetMissing("/${projectA12.organisation}/${projectA12.module}/${projectA12.revision}/ivy-${projectA12.revision}.xml")
        server.expectGetMissing("/${projectA11.organisation}/${projectA11.module}/${projectA11.revision}/ivy-${projectA11.revision}.xml")

        // TODO:DAZ Should not list twice
        server.expectGetDirectoryListing("/${projectA12.organisation}/${projectA12.module}/", projectA12.moduleDir.parentFile)
        server.expectHead("/${projectA12.organisation}/${projectA12.module}/${projectA12.revision}/${projectA12.module}-${projectA12.revision}.jar", projectA12.jarFile)
        server.expectGet("/${projectA12.organisation}/${projectA12.module}/${projectA12.revision}/${projectA12.module}-${projectA12.revision}.jar", projectA12.jarFile)

        and:
        run 'retrieve'

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def "uses latest version with correct status for latest.release and latest.milestone"() {
        server.start()
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}/repo"
    }
}

configurations {
    release
    milestone
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
}

dependencies {
    release group: "group", name: "projectA", version: "latest.release"
    milestone group: "group", name: "projectA", version: "latest.milestone"
}

task retrieve(dependsOn: ['retrieveRelease', 'retrieveMilestone'])

task retrieveRelease(type: Sync) {
    from configurations.release
    into 'release'
}

task retrieveMilestone(type: Sync) {
    from configurations.milestone
    into 'milestone'
}
"""

        when: "Versions are published"
        repo.module("group", "projectA", "1.0").withStatus('release').publish()
        repo.module('group', 'projectA', '1.1').withStatus('milestone').publish()
        repo.module('group', 'projectA', '1.2').withStatus('integration').publish()
        repo.module("group", "projectA", "2.0").withStatus('release').publish()
        repo.module('group', 'projectA', '2.1').withStatus('milestone').publish()
        repo.module('group', 'projectA', '2.2').withStatus('integration').publish()

        and: "Server handles requests"
        server.allowGetOrHead('/repo', repo.rootDir)

        and:
        run 'retrieve'

        then:
        file('release').assertHasDescendants('projectA-2.0.jar')
        file('milestone').assertHasDescendants('projectA-2.1.jar')
    }

    @Ignore("Fails with Nullpointerexception originally caused by unclosed HttpExternalResource")
    def "can use latest version from different remote repositories"() {
        server.start()
        def repo1 = ivyRepo("ivy1")
        def repo2 = ivyRepo("ivy2")

        given:
        buildFile << """
    repositories {
        ivy {
            url "http://localhost:${server.port}/repo1"
            url "http://localhost:${server.port}/repo2"
        }
    }

    configurations {
        milestone
    }

    configurations.all {
        resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
    }

    dependencies {
        milestone group: "group", name: "projectA", version: "latest.milestone"
    }

    task retrieveMilestone(type: Sync) {
        from configurations.milestone
        into 'milestone'
    }
    """

        when: "Versions are published"
        repo1.module('group', 'projectA', '1.1').withStatus('milestone').publish()
        repo2.module('group', 'projectA', '1.2').withStatus('integration').publish()

        and: "Server handles requests"
        server.allowGetOrHead('/repo1', repo1.rootDir)
        server.allowGetOrHead('/repo2', repo2.rootDir)

        and:
        run 'retrieveMilestone'

        then:
        file('milestone').assertHasDescendants('projectA-1.1.jar')
    }

    def "checks new repositories before returning any cached value"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo1" }
}

if (project.hasProperty('addRepo2')) {
    repositories {
        ivy { url "http://localhost:${server.port}/repo2" }
    }
}

configurations { compile }

dependencies {
    compile group: "group", name: "projectA", version: "1.+"
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        def projectA11 = ivyRepo('repo1').module("group", "projectA", "1.1")
        projectA11.publish()
        def projectA12 = ivyRepo('repo2').module("group", "projectA", "1.2")
        projectA12.publish()

        and: "Server handles requests"
        serveUpDynamicRevision(projectA11, "/repo1")

        and: "Retrieve with only repo1"
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when: "Server handles requests"
        server.resetExpectations()
        serveUpDynamicRevision(projectA12, "/repo2")

        and: "Retrieve with both repos"
        executer.withArguments("-PaddRepo2")
        run 'retrieve'

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def "does not cache information about broken modules"() {
        server.start()

        given:
        buildFile << """
    repositories {
        ivy { url "http://localhost:${server.port}/repo1" }
        ivy { url "http://localhost:${server.port}/repo2" }
    }

    configurations { compile }

    dependencies {
        compile group: "group", name: "projectA", version: "1.+"
    }

    task retrieve(type: Sync) {
        from configurations.compile
        into 'libs'
    }
    """

        when:
        def projectA11 = ivyRepo('repo1').module("group", "projectA", "1.2")
        projectA11.publish()
        def projectA12 = ivyRepo('repo2').module("group", "projectA", "1.1")
        projectA12.publish()

        and: "projectA is broken in repo1"
        server.addBroken("/repo1/group/projectA/")
        serveUpDynamicRevision(projectA12, "/repo2")

        and: "Retrieve with only repo2"
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when: "Server handles requests"
        server.resetExpectations()
        serveUpDynamicRevision(projectA11, "/repo1")

        and: "Retrieve with both repos"
        run 'retrieve'

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def "uses and caches latest of versions obtained from multiple HTTP repositories"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo1" }
    ivy { url "http://localhost:${server.port}/repo2" }
    ivy { url "http://localhost:${server.port}/repo3" }
}

configurations { compile }

dependencies {
    compile group: "group", name: "projectA", version: "1.+"
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when: "Versions are published"
        def projectA11 = ivyRepo('repo1').module("group", "projectA", "1.1")
        projectA11.publish()
        def projectA12 = ivyRepo('repo3').module("group", "projectA", "1.2")
        projectA12.publish()

        and: "Server handles requests"
        server.expectGetDirectoryListing("/repo1/group/projectA/", projectA11.moduleDir.parentFile)
        // TODO Should not need to get this
        server.expectGet("/repo1/group/projectA/1.1/ivy-1.1.xml", projectA11.ivyFile)
        // TODO Should only list missing directory once
        server.expectGetMissing("/repo2/group/projectA/")
        server.expectGetMissing("/repo2/group/projectA/")
        serveUpDynamicRevision(projectA12, "/repo3")

        and:
        run 'retrieve'

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when: "Run again with cached dependencies"
        server.resetExpectations()
        def result = run 'retrieve'

        then: "No server requests, task skipped"
        result.assertTaskSkipped(':retrieve')
    }

    def "caches resolved revisions until cache expiry"() {
        server.start()
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}

configurations { compile }

dependencies {
    compile group: "group", name: "projectA", version: "1.+"
}

if (project.hasProperty('noDynamicRevisionCache')) {
    configurations.all {
        resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
    }
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when: "Version 1.1 is published"
        def version1 = repo.module("group", "projectA", "1.1")
        version1.publish()

        and: "Server handles requests"
        serveUpDynamicRevision(version1)

        and: "We request 1.+"
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(version1.jarFile)

        when: "Version 1.2 is published"
        def version2 = repo.module("group", "projectA", "1.2")
        version2.publish()

        and: "We request 1.+, with dynamic mappings cached. No server requests."
        run 'retrieve'

        then: "Version 1.1 is still used, as the 1.+ -> 1.1 mapping is cached"
        file('libs').assertHasDescendants('projectA-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(version1.jarFile)

        when: "Server handles requests"
        serveUpDynamicRevision(version2)

        and: "We request 1.+, with zero expiry for dynamic revision cache"
        executer.withArguments("-d", "-PnoDynamicRevisionCache").withTasks('retrieve').run()

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(version2.jarFile)
    }

    def "uses and caches dynamic revisions for transitive dependencies"() {
        server.start()
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
    }
}

configurations { compile }

dependencies {
    compile group: "group", name: "main", version: "1.0"
}

if (project.hasProperty('noDynamicRevisionCache')) {
    configurations.all {
        resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
    }
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when: "Version is published"
        def mainProject = repo.module("group", "main", "1.0")
        mainProject.dependsOn("group", "projectA", "1.+")
        mainProject.dependsOn("group", "projectB", "latest.integration")
        mainProject.publish()

        and: "transitive dependencies have initial values"
        def projectA1 = repo.module("group", "projectA", "1.1")
        projectA1.publish()
        def projectB1 = repo.module("group", "projectB", "1.1")
        projectB1.publish()

        and: "Server handles requests"
        server.expectGet("/group/main/1.0/ivy-1.0.xml", mainProject.ivyFile)
        server.expectGet("/group/main/1.0/main-1.0.jar", mainProject.jarFile)
        serveUpDynamicRevision(projectA1)
        serveUpDynamicRevision(projectB1)

        and:
        run 'retrieve'

        then: "Initial transitive dependencies are used"
        file('libs').assertHasDescendants('main-1.0.jar', 'projectA-1.1.jar', 'projectB-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.1.jar').assertIsCopyOf(projectB1.jarFile)

        when: "New versions are published"
        def projectA2 = repo.module("group", "projectA", "1.2")
        projectA2.publish()
        def projectB2 = repo.module("group", "projectB", "2.2")
        projectB2.publish()

        and: "No server requests"
        server.resetExpectations()

        and:
        run 'retrieve'

        then: "Cached versions are used"
        file('libs').assertHasDescendants('main-1.0.jar', 'projectA-1.1.jar', 'projectB-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.1.jar').assertIsCopyOf(projectB1.jarFile)

        when: "Server handles requests"
        server.resetExpectations()
        serveUpDynamicRevision(projectA2)
        serveUpDynamicRevision(projectB2)

        and: "DynamicRevisionCache is bypassed"
        executer.withArguments("-PnoDynamicRevisionCache")
        run 'retrieve'

        then: "New versions are used"
        file('libs').assertHasDescendants('main-1.0.jar', 'projectA-1.2.jar', 'projectB-2.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(projectA2.jarFile)
        file('libs/projectB-2.2.jar').assertIsCopyOf(projectB2.jarFile)
    }

    public void "resolves dynamic version with 2 repositories where first repo results in 404 for directory listing"() {
        server.start()
        given:
        def repo = ivyRepo()
        def moduleA = repo.module('group', 'projectA').publish()

        and:
        buildFile << """
            repositories {
                ivy { url "http://localhost:${server.port}/repo1" }
                ivy { url "http://localhost:${server.port}/repo2" }
            }
            configurations { compile }
            dependencies {
                compile 'group:projectA:1.+'
            }
            task listJars << {
                assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
            }
            """

        when:
        server.expectGetMissing('/repo1/group/projectA/')
        server.expectGetMissing('/repo1/group/projectA/')
        server.expectGetDirectoryListing("/repo2/group/projectA/", moduleA.moduleDir.parentFile)
        server.expectGet('/repo2/group/projectA/1.0/ivy-1.0.xml', moduleA.ivyFile)
        server.expectGet('/repo2/group/projectA/1.0/projectA-1.0.jar', moduleA.jarFile)

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies
        then:
        succeeds('listJars')
    }

    private def serveUpDynamicRevision(IvyModule module, String prefix = "") {
        server.expectGetDirectoryListing("${prefix}/${module.organisation}/${module.module}/", module.moduleDir.parentFile)
        server.expectGet("${prefix}/${module.organisation}/${module.module}/${module.revision}/ivy-${module.revision}.xml", module.ivyFile)
        server.expectGet("${prefix}/${module.organisation}/${module.module}/${module.revision}/${module.module}-${module.revision}.jar", module.jarFile)
    }
}
