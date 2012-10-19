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
import spock.lang.Ignore

class IvyDynamicRevisionRemoteResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "uses latest version from version range and latest status"() {
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
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        ivyHttpRepo.module("group", "projectA", "2.0").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()

        and: "Server handles requests"
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        projectA1.expectIvyGet()
        projectA1.expectJarGet()
        ivyHttpRepo.expectDirectoryListGet("group", "projectB")
        projectB1.expectIvyGet()
        projectB1.expectJarGet()

        and:
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.1.jar').assertIsCopyOf(projectB1.jarFile)

        when: "New versions are published"
        def projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()
        def projectB2 = ivyHttpRepo.module("group", "projectB", "2.2").publish()

        and: "Server handles requests"
        server.resetExpectations()
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        projectA2.expectIvyGet()
        projectA2.expectJarGet()
        ivyHttpRepo.expectDirectoryListGet("group", "projectB")
        projectB2.expectIvyGet()
        projectB2.expectJarGet()

        and:
        run 'retrieve'

        then: "New versions are used"
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-2.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(projectA2.jarFile)
        file('libs/projectB-2.2.jar').assertIsCopyOf(projectB2.jarFile)
    }

    def "determines latest version with jar only"() {
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
  compile group: "group", name: "projectA", version: "1.+"
}

task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""

        when: "Version 1.1 is published"
        def projectA11 = ivyHttpRepo.module("group", "projectA", "1.1").withNoMetaData().publish()
        def projectA12 = ivyHttpRepo.module("group", "projectA", "1.2").withNoMetaData().publish()
        ivyHttpRepo.module("group", "projectA", "2.0").withNoMetaData().publish()

        and: "Server handles requests"
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        projectA12.expectIvyGetMissing()
        projectA11.expectIvyGetMissing()

        // TODO - Should not list twice
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        projectA12.expectJarHead()
        projectA12.expectJarGet()

        and:
        run 'retrieve'

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def "uses latest version with correct status for latest.release and latest.milestone"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}

configurations {
    release
    milestone
}

dependencies {
    release group: "group", name: "projectA", version: "latest.release"
    milestone group: "group", name: "projectA", version: "latest.milestone"
}

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
        ivyHttpRepo.module("group", "projectA", "1.0").withStatus('release').publish()
        ivyHttpRepo.module('group', 'projectA', '1.1').withStatus('milestone').publish()
        ivyHttpRepo.module('group', 'projectA', '1.2').withStatus('integration').publish()
        def release = ivyHttpRepo.module("group", "projectA", "2.0").withStatus('release').publish()
        def milestone = ivyHttpRepo.module('group', 'projectA', '2.1').withStatus('milestone').publish()
        def integration = ivyHttpRepo.module('group', 'projectA', '2.2').withStatus('integration').publish()

        and: "Server handles requests"
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        integration.expectIvyGet()
        milestone.expectIvyGet()
        release.expectIvyGet()
        release.expectJarGet()

        and:
        run 'retrieveRelease'

        then:
        file('release').assertHasDescendants('projectA-2.0.jar')

        when:
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        integration.expectIvyHead()
        milestone.expectIvyHead()
        milestone.expectJarGet()

        and:
        run 'retrieveMilestone'

        then:
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
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")

        given:
        buildFile << """
repositories {
    ivy { url "${repo1.uri}" }
}

if (project.hasProperty('addRepo2')) {
    repositories {
        ivy { url "${repo2.uri}" }
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
        def projectA11 = repo1.module("group", "projectA", "1.1").publish()
        def projectA12 = repo2.module("group", "projectA", "1.2").publish()

        and: "Server handles requests"
        repo1.expectDirectoryListGet("group", "projectA")
        projectA11.expectIvyGet()
        projectA11.expectJarGet()

        and: "Retrieve with only repo1"
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when: "Server handles requests"
        server.resetExpectations()
        repo2.expectDirectoryListGet("group", "projectA")
        projectA12.expectIvyGet()
        projectA12.expectJarGet()

        and: "Retrieve with both repos"
        executer.withArguments("-PaddRepo2")
        run 'retrieve'

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def "does not cache information about broken modules"() {
        server.start()
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")

        given:
        buildFile << """
    repositories {
        ivy { url "${repo1.uri}" }
        ivy { url "${repo2.uri}" }
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
        def projectA12 = repo1.module("group", "projectA", "1.2").publish()
        def projectA11 = repo2.module("group", "projectA", "1.1").publish()

        and: "projectA is broken in repo1"
        server.addBroken("/repo1/group/projectA/")
        repo2.expectDirectoryListGet("group", "projectA")
        projectA11.expectIvyGet()
        projectA11.expectJarGet()

        and: "Retrieve with only repo2"
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when: "Server handles requests"
        server.resetExpectations()
        repo1.expectDirectoryListGet("group", "projectA")
        projectA12.expectIvyGet()
        projectA12.expectJarGet()

        and: "Retrieve with both repos"
        run 'retrieve'

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }

    def "uses and caches latest of versions obtained from multiple HTTP repositories"() {
        server.start()
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def repo3 = ivyHttpRepo("repo3")

        given:
        buildFile << """
repositories {
    ivy { url "${repo1.uri}" }
    ivy { url "${repo2.uri}" }
    ivy { url "${repo3.uri}" }
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
        def projectA11 = repo1.module("group", "projectA", "1.1").publish()
        def projectA12 = repo3.module("group", "projectA", "1.2").publish()

        and: "Server handles requests"
        repo1.expectDirectoryListGet("group", "projectA")
        // TODO Should not need to get this
        projectA11.expectIvyGet()
        // TODO Should only list missing directory once
        repo2.expectDirectoryListGet("group", "projectA")
        repo2.expectDirectoryListGet("group", "projectA")
        repo3.expectDirectoryListGet("group", "projectA")
        projectA12.expectIvyGet()
        projectA12.expectJarGet()

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

    def "reuses cached artifacts that match multiple dynamic versions"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "${ivyHttpRepo.uri}" }
}

configurations { deps1; deps2 }

dependencies {
    deps1 group: "org.test", name: "projectA", version: "1.+"
    deps2 group: "org.test", name: "projectA", version: "[1.0,2.0)"
}

task retrieve1(type: Sync) {
    from configurations.deps1
    into 'libs1'
}
task retrieve2(type: Sync) {
    from configurations.deps2
    into 'libs2'
}
"""

        when:
        ivyHttpRepo.module("org.test", "projectA", "1.1").publish()
        def projectA12 = ivyHttpRepo.module("org.test", "projectA", "1.2").publish()

        and:
        ivyHttpRepo.expectDirectoryListGet("org.test", "projectA")
        projectA12.expectIvyGet()
        projectA12.expectJarGet()

        and:
        run 'retrieve1'

        then:
        file('libs1').assertHasDescendants('projectA-1.2.jar')

        when:
        server.resetExpectations()
        ivyHttpRepo.expectDirectoryListGet("org.test", "projectA")
        projectA12.expectIvyHead()

        and:
        run 'retrieve2'

        then:
        file('libs1').assertHasDescendants('projectA-1.2.jar')
    }

    def "caches resolved revisions until cache expiry"() {
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
        def version1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()

        and: "Server handles requests"
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        version1.expectIvyGet()
        version1.expectJarGet()

        and: "We request 1.+"
        run 'retrieve'

        then: "Version 1.1 is used"
        file('libs').assertHasDescendants('projectA-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(version1.jarFile)

        when: "Version 1.2 is published"
        def version2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()

        and: "We request 1.+, with dynamic mappings cached. No server requests."
        run 'retrieve'

        then: "Version 1.1 is still used, as the 1.+ -> 1.1 mapping is cached"
        file('libs').assertHasDescendants('projectA-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(version1.jarFile)

        when: "Server handles requests"
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        version2.expectIvyGet()
        version2.expectJarGet()

        and: "We request 1.+, with zero expiry for dynamic revision cache"
        executer.withArguments("-PnoDynamicRevisionCache").withTasks('retrieve').run()

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(version2.jarFile)
    }

    def "uses and caches dynamic revisions for transitive dependencies"() {
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
        def mainProject = ivyHttpRepo.module("group", "main", "1.0")
        mainProject.dependsOn("group", "projectA", "1.+")
        mainProject.dependsOn("group", "projectB", "latest.integration")
        mainProject.publish()

        and: "transitive dependencies have initial values"
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()

        and: "Server handles requests"
        mainProject.expectIvyGet()
        mainProject.expectJarGet()
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        projectA1.expectIvyGet()
        projectA1.expectJarGet()
        ivyHttpRepo.expectDirectoryListGet("group", "projectB")
        projectB1.expectIvyGet()
        projectB1.expectJarGet()

        and:
        run 'retrieve'

        then: "Initial transitive dependencies are used"
        file('libs').assertHasDescendants('main-1.0.jar', 'projectA-1.1.jar', 'projectB-1.1.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.1.jar').assertIsCopyOf(projectB1.jarFile)

        when: "New versions are published"
        def projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()
        def projectB2 = ivyHttpRepo.module("group", "projectB", "2.2").publish()

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
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        projectA2.expectIvyGet()
        projectA2.expectJarGet()
        ivyHttpRepo.expectDirectoryListGet("group", "projectB")
        projectB2.expectIvyGet()
        projectB2.expectJarGet()

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
        def repo = ivyHttpRepo("repo2")
        def moduleA = repo.module('group', 'projectA').publish()

        and:
        buildFile << """
            repositories {
                ivy { url "http://localhost:${server.port}/repo1" }
                ivy { url "${repo.uri}" }
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
        // TODO - should only list versions once
        server.expectGetMissing('/repo1/group/projectA/')
        repo.expectDirectoryListGet("group", "projectA")
        moduleA.expectIvyGet()
        moduleA.expectJarGet()

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies
        then:
        succeeds('listJars')
    }

    def "reuses cached artifacts across repository types"() {
        server.start()
        def ivyRepo = ivyHttpRepo('repo1')
        def mavenRepo = mavenHttpRepo('repo2')
        def ivyModule = ivyRepo.module("org.test", "a", "1.1").publish()
        def mavenModule = mavenRepo.module("org.test", "a", "1.1").publish()
        assert ivyModule.jarFile.bytes == mavenModule.artifactFile.bytes

        given:
        buildFile.text = """
repositories {
    ivy { url '${ivyRepo.uri}' }
}

configurations { compile }

dependencies {
    compile 'org.test:a:1+'
}

task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""

        when:
        ivyRepo.expectDirectoryListGet("org.test", "a")
        ivyModule.expectIvyGet()
        ivyModule.expectJarGet()

        and:
        run 'retrieve'

        then:
        file('build').assertHasDescendants('a-1.1.jar')

        when:
        buildFile.text = """
repositories {
    maven { url '${mavenRepo.uri}' }
}

configurations { compile }

dependencies {
    compile 'org.test:a:[1.0,2.0)'
}

task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""

        and:
        mavenRepo.expectMetaDataGet("org.test", "a")
        mavenModule.expectPomGet()
        mavenModule.expectArtifactHead()
        mavenModule.expectArtifactSha1Get()

        and:
        run 'retrieve'

        then:
        file('build').assertHasDescendants('a-1.1.jar')
    }
}
