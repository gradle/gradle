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

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.IvyModule
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.junit.Rule

class IvyDynamicRevisionRemoteResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    def "uses latest version from version range and latest status"() {
        distribution.requireOwnUserHomeDir()
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
        def projectA1 = repo.module("group", "projectA", "1.1")
        projectA1.publish()
        def projectB1 = repo.module("group", "projectB", "1.1")
        projectB1.publish()

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
        def projectA2 = repo.module("group", "projectA", "1.2")
        projectA2.publish()
        def projectB2 = repo.module("group", "projectB", "2.2")
        projectB2.publish()

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

    def "caches resolved revisions until cache expiry"() {
        distribution.requireOwnUserHomeDir()
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
        executer.withDeprecationChecksDisabled().withArguments("-d", "-PnoDynamicRevisionCache").withTasks('retrieve').run()

        then: "Version 1.2 is used"
        file('libs').assertHasDescendants('projectA-1.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(version2.jarFile)
    }

    def "uses and caches dynamic revisions for transitive dependencies"() {
        distribution.requireOwnUserHomeDir()
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
        executer.withDeprecationChecksDisabled().withArguments("-d", "-PnoDynamicRevisionCache").withTasks('retrieve').run()

        then: "New versions are used"
        file('libs').assertHasDescendants('main-1.0.jar', 'projectA-1.2.jar', 'projectB-2.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(projectA2.jarFile)
        file('libs/projectB-2.2.jar').assertIsCopyOf(projectB2.jarFile)
    }

    private def serveUpDynamicRevision(IvyModule module) {
        server.expectGetDirectoryListing("/${module.organisation}/${module.module}/", module.moduleDir.parentFile)
        server.expectGet("/${module.organisation}/${module.module}/${module.revision}/ivy-${module.revision}.xml", module.ivyFile)
        server.expectGet("/${module.organisation}/${module.module}/${module.revision}/${module.module}-${module.revision}.jar", module.jarFile)
    }

    def "detects changed module descriptor when flagged as changing"() {
        distribution.requireOwnUserHomeDir()
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}

configurations { compile }

configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}

dependencies {
    compile group: "group", name: "projectA", version: "1.1", changing: true
}

task retrieve(type: Copy) {
    into 'build'
    from configurations.compile
}
"""

        when: "Version 1.1 is published"
        def module = ivyRepo().module("group", "projectA", "1.1")
        module.publish()

        and: "Server handles requests"
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)

        and: "We request 1.1 (changing)"
        run 'retrieve'

        then: "Version 1.1 jar is downloaded"
        file('build').assertHasDescendants('projectA-1.1.jar')

        when: "Module meta-data is changed (new artifact)"
        module.artifact([name: 'other'])
        module.dependsOn("group", "projectB", "2.0")
        module.publish()
        def moduleB = ivyRepo().module("group", "projectB", "2.0")
        moduleB.publish();

        and: "Server handles requests"
        server.resetExpectations()
        // Server will be hit to get updated versions
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)
        server.expectGet('/repo/group/projectA/1.1/other-1.1.jar', module.moduleDir.file('other-1.1.jar'))
        server.expectGet('/repo/group/projectB/2.0/ivy-2.0.xml', moduleB.ivyFile)
        server.expectGet('/repo/group/projectB/2.0/projectB-2.0.jar', moduleB.jarFile)

        and: "We request 1.1 again"
        run 'retrieve'

        then: "We get all artifacts, including the new ones"
        file('build').assertHasDescendants('projectA-1.1.jar', 'other-1.1.jar', 'projectB-2.0.jar')
    }

    def "detects changed artifact when flagged as changing"() {
        distribution.requireOwnUserHomeDir()
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}

configurations { compile }

configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}

dependencies {
    compile group: "group", name: "projectA", version: "1.1", changing: true
}

task retrieve(type: Copy) {
    into 'build'
    from configurations.compile
}
"""

        and:
        def module = ivyRepo().module("group", "projectA", "1.1")
        module.publish()

        when:
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)

        run 'retrieve'

        then:
        def jarFile = file('build/projectA-1.1.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when:
        module.publishWithChangedContent()
        waitOneSecondSoThatPublicationDateWillHaveChanged();

        server.resetExpectations()
        // Server will be hit to get updated versions
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)

        run 'retrieve'

        then:
        def changedJarFile = file('build/projectA-1.1.jar')
        changedJarFile.assertHasChangedSince(snapshot)
        changedJarFile.assertIsCopyOf(module.jarFile)

    }

    def "caches changing module descriptor and artifacts until cache expiry"() {
        distribution.requireOwnUserHomeDir()
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}

configurations { compile }


if (project.hasProperty('noDynamicRevisionCache')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}

dependencies {
    compile group: "group", name: "projectA", version: "1.1", changing: true
}

task retrieve(type: Copy) {
    into 'build'
    from configurations.compile
}
"""

        when: "Version 1.1 is published"
        def module = ivyRepo().module("group", "projectA", "1.1")
        module.publish()

        and: "Server handles requests"
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)

        and: "We request 1.1 (changing)"
        run 'retrieve'

        then: "Version 1.1 jar is downloaded"
        file('build').assertHasDescendants('projectA-1.1.jar')
        def jarFile = file('build/projectA-1.1.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when: "Module meta-data is changed and artifacts are modified"
        module.artifact([name: 'other'])
        module.publishWithChangedContent()
        waitOneSecondSoThatPublicationDateWillHaveChanged()

        and: "We request 1.1 (changing), with module meta-data cached. No server requests."
        run 'retrieve'

        then: "Original module meta-data is used"
        file('build').assertHasDescendants('projectA-1.1.jar')

        // and: "Original artifacts are used"
        file('build').assertHasDescendants('projectA-1.1.jar')
        jarFile.assertHasNotChangedSince(snapshot)

        when: "Server handles requests"
        server.resetExpectations()
        // Server will be hit to get updated versions
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)
        server.expectGet('/repo/group/projectA/1.1/other-1.1.jar', module.moduleDir.file('other-1.1.jar'))

        and: "We request 1.1 (changing) again, with zero expiry for dynamic revision cache"
        executer.withDeprecationChecksDisabled().withArguments("-d", "-PnoDynamicRevisionCache").withTasks('retrieve').run()

        then: "We get new artifacts based on the new meta-data"
        file('build').assertHasDescendants('projectA-1.1.jar', 'other-1.1.jar')
        jarFile.assertHasChangedSince(snapshot)
        jarFile.assertIsCopyOf(module.jarFile)
    }

    private def waitOneSecondSoThatPublicationDateWillHaveChanged() {
        // TODO:DAZ Remove this
        // Ivy checks the publication date to see if it's _really_ changed, won't delete the artifacts if not.
        // So wait a second to ensure the date will be different.
        Thread.sleep(1000)
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(distribution.testFile('ivy-repo'))
    }
}
