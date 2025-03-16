/*
 * Copyright 2012 the original author or authors.
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

class IvyChangingModuleRemoteResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def runRetrieveTask() {
        executer.withArgument("--no-problems-report")
        run 'retrieve'
    }

    def "detects changed module descriptor when flagged as changing"() {
        given:
        buildFile << """
repositories {
    ivy { url = "${ivyHttpRepo.uri}" }
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
        def module = ivyHttpRepo.module("group", "projectA", "1.1").publish()

        and: "Server handles requests"
        module.ivy.expectGet()
        module.jar.expectGet()

        and: "We request 1.1 (changing)"
        runRetrieveTask()

        then: "Version 1.1 jar is downloaded"
        file('build').assertHasDescendants('projectA-1.1.jar')

        when: "Module meta-data is changed (new artifact)"
        module.artifact([name: 'other'])
        module.dependsOn("group", "projectB", "2.0")
        module.publish()
        def moduleB = ivyHttpRepo.module("group", "projectB", "2.0").publish()

        and: "Server handles requests"
        server.resetExpectations()
        // Server will be hit to get updated versions
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()
        module.jar.expectHead()
        module.getArtifact(name: 'other').expectGet()
        moduleB.ivy.expectGet()
        moduleB.jar.expectGet()

        and: "We request 1.1 again"
        executer.withArgument("--no-problems-report")
        runRetrieveTask()

        then: "We get all artifacts, including the new ones"
        file('build').assertHasDescendants('projectA-1.1.jar', 'other-1.1.jar', 'projectB-2.0.jar')
    }

    def "can mark a module as changing after first retrieval"() {
        given:
        buildFile << """
def isChanging = providers.gradleProperty('isChanging').isPresent()
repositories {
    ivy { url = "${ivyHttpRepo.uri}" }
}

configurations { compile }
configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'

dependencies {
    compile group: "group", name: "projectA", version: "1.1", changing: isChanging
}

task retrieve(type: Copy) {
    into 'build'
    from configurations.compile
}
"""
        and:
        def module = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        module.allowAll()

        when: 'original retrieve'
        runRetrieveTask()

        then:
        def jarSnapshot = file('build/projectA-1.1.jar').snapshot()

        when:
        server.resetExpectations()
        module.publishWithChangedContent()
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()
        module.jar.expectHead()
        module.jar.sha1.expectGet()
        module.jar.expectGet()

        and:
        executer.withArgument('-PisChanging')
        runRetrieveTask()

        then:
        file('build/projectA-1.1.jar').assertHasChangedSince(jarSnapshot)
    }

    def "detects changed artifact when flagged as changing"() {
        given:
        buildFile << """
repositories {
    ivy { url = "${ivyHttpRepo.uri}" }
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
        def module = ivyHttpRepo.module("group", "projectA", "1.1").publish()

        when:
        module.ivy.expectGet()
        module.jar.expectGet()

        runRetrieveTask()

        then:
        def jarFile = file('build/projectA-1.1.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when:
        module.publishWithChangedContent()

        server.resetExpectations()
        // Server will be hit to get updated versions
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()
        module.jar.expectHead()
        module.jar.sha1.expectGet()
        module.jar.expectGet()

        runRetrieveTask()

        then:
        def changedJarFile = file('build/projectA-1.1.jar')
        changedJarFile.assertHasChangedSince(snapshot)
        changedJarFile.assertIsCopyOf(module.jarFile)
    }

    def "caches changing module descriptor and artifacts until cache expiry"() {
        given:
        buildFile << """
repositories {
    ivy { url = "${ivyHttpRepo.uri}" }
}

configurations { compile }


if (providers.gradleProperty('doNotCacheChangingModules').isPresent()) {
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
        def module = ivyHttpRepo.module("group", "projectA", "1.1").publish()

        and: "Server handles requests"
        module.ivy.expectGet()
        module.jar.expectGet()

        and: "We request 1.1 (changing)"
        runRetrieveTask()

        then: "Version 1.1 jar is downloaded"
        file('build/reports').maybeDeleteDir() // delete configuration cache report if present
        file('build').assertHasDescendants('projectA-1.1.jar')
        def jarFile = file('build/projectA-1.1.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when: "Module meta-data is changed and artifacts are modified"
        server.resetExpectations()
        module.artifact([name: 'other'])
        module.publishWithChangedContent()

        and: "We request 1.1 (changing), with module meta-data cached. No server requests."
        runRetrieveTask()

        then: "Original module meta-data and artifacts are used"
        file('build/reports').maybeDeleteDir() // delete configuration cache report if present
        file('build').assertHasDescendants('projectA-1.1.jar')
        jarFile.assertHasNotChangedSince(snapshot)

        when: "Server handles requests"
        server.resetExpectations()
        // Server will be hit to get updated versions
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()
        module.jar.expectHead()
        module.jar.sha1.expectGet()
        module.jar.expectGet()
        module.getArtifact(name: 'other').expectGet()

        and: "We request 1.1 (changing) again, with zero expiry for dynamic revision cache"
        executer.withArguments("-PdoNotCacheChangingModules")
        runRetrieveTask()

        then: "We get new artifacts based on the new meta-data"
        file('build/reports').maybeDeleteDir() // delete configuration cache report if present
        file('build').assertHasDescendants('projectA-1.1.jar', 'other-1.1.jar')
        jarFile.assertHasChangedSince(snapshot)
        jarFile.assertIsCopyOf(module.jarFile)
    }

    def "avoid redownload unchanged artifact when no checksum available"() {
        given:
        buildFile << """
            repositories {
                ivy { url = "${ivyHttpRepo.uri}" }
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
        def module = ivyHttpRepo.module("group", "projectA", "1.1").publish()

        when:
        module.ivy.expectGet()
        module.jar.expectGet()

        and:
        runRetrieveTask()

        then:
        def downloadedJar = file('build/projectA-1.1.jar')
        downloadedJar.assertIsCopyOf(module.jarFile)
        def snapshot = downloadedJar.snapshot()

        when:
        server.resetExpectations()
        module.ivy.expectHead()
        module.jar.expectHead()

        and:
        runRetrieveTask()

        then:
        downloadedJar.assertHasNotChangedSince(snapshot)

        when:
        // Do change the jar, so we can check that the new version wasn't downloaded
        module.publishWithChangedContent()

        server.resetExpectations()
        module.ivy.expectHead()
        module.ivy.sha1.expectGetMissing()
        module.ivy.expectGet()
        module.jar.expectHead()
        module.jar.sha1.expectGetMissing()
        module.jar.expectGet()

        runRetrieveTask()

        then:
        downloadedJar.assertHasChangedSince(snapshot)
        downloadedJar.assertIsCopyOf(module.jarFile)
    }
}
