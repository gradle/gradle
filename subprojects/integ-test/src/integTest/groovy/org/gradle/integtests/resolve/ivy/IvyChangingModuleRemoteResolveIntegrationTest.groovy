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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class IvyChangingModuleRemoteResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "detects changed module descriptor when flagged as changing"() {
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
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml.sha1', module.sha1File(module.ivyFile))
        server.expectHeadThenGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectHead('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar.sha1', module.sha1File(module.jarFile))
        server.expectGet('/repo/group/projectA/1.1/other-1.1.jar', module.moduleDir.file('other-1.1.jar'))
        server.expectGet('/repo/group/projectB/2.0/ivy-2.0.xml', moduleB.ivyFile)
        server.expectGet('/repo/group/projectB/2.0/projectB-2.0.jar', moduleB.jarFile)

        and: "We request 1.1 again"
        run 'retrieve'

        then: "We get all artifacts, including the new ones"
        file('build').assertHasDescendants('projectA-1.1.jar', 'other-1.1.jar', 'projectB-2.0.jar')
    }

    def "can mark a module as changing after first retrieval"() {
        server.start()

        given:
        buildFile << """
def isChanging = project.hasProperty('isChanging') ? true : false
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
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
        def module = ivyRepo().module("group", "projectA", "1.1")
        module.publish()
        server.allowGetOrHead('/repo', ivyRepo().rootDir)

        when: 'original retrieve'
        run 'retrieve'

        then:
        def jarSnapshot = file('build/projectA-1.1.jar').snapshot()

        when:
        module.publishWithChangedContent()

        and:
        executer.withArguments('-PisChanging')
        run 'retrieve'

        then:
        file('build/projectA-1.1.jar').assertHasChangedSince(jarSnapshot)
    }

    def "detects changed artifact when flagged as changing"() {
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
        def module = ivyRepo().module("group", "projectA", "1.1").publish()

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

        server.resetExpectations()
        // Server will be hit to get updated versions
        module.expectIvyHead(server, '/repo')
        module.expectIvySha1Get(server, '/repo')
        module.expectIvyGet(server, '/repo')
        module.expectArtifactHead(server, '/repo')
        module.expectArtifactSha1Get(server, '/repo')
        module.expectArtifactGet(server, '/repo')

        run 'retrieve'

        then:
        def changedJarFile = file('build/projectA-1.1.jar')
        changedJarFile.assertHasChangedSince(snapshot)
        changedJarFile.assertIsCopyOf(module.jarFile)
    }

    def "caches changing module descriptor and artifacts until cache expiry"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}

configurations { compile }


if (project.hasProperty('doNotCacheChangingModules')) {
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
        def module = ivyRepo().module("group", "projectA", "1.1").publish()

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

        and: "We request 1.1 (changing), with module meta-data cached. No server requests."
        run 'retrieve'

        then: "Original module meta-data and artifacts are used"
        file('build').assertHasDescendants('projectA-1.1.jar')
        jarFile.assertHasNotChangedSince(snapshot)

        when: "Server handles requests"
        server.resetExpectations()
        // Server will be hit to get updated versions
        server.expectGet('/repo/group/projectA/1.1/ivy-1.1.xml.sha1', module.sha1File(module.ivyFile))
        server.expectHeadThenGet('/repo/group/projectA/1.1/ivy-1.1.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.1/projectA-1.1.jar.sha1', module.sha1File(module.jarFile))
        server.expectHeadThenGet('/repo/group/projectA/1.1/projectA-1.1.jar', module.jarFile)
        server.expectGet('/repo/group/projectA/1.1/other-1.1.jar', module.moduleDir.file('other-1.1.jar'))

        and: "We request 1.1 (changing) again, with zero expiry for dynamic revision cache"
        executer.withArguments("-PdoNotCacheChangingModules")
        run 'retrieve'

        then: "We get new artifacts based on the new meta-data"
        file('build').assertHasDescendants('projectA-1.1.jar', 'other-1.1.jar')
        jarFile.assertHasChangedSince(snapshot)
        jarFile.assertIsCopyOf(module.jarFile)
    }

    def "can use cache-control DSL to mimic changing pattern for ivy repository"() {
        server.start()

        given:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}

configurations { compile }

import static java.util.concurrent.TimeUnit.SECONDS
configurations.all {
    resolutionStrategy.resolutionRules.with {
        eachModule({ moduleResolve ->
            if (moduleResolve.request.version.endsWith('-CHANGING')) {
                moduleResolve.cacheFor(0, SECONDS)
            }
        } as Action)

        eachArtifact({ artifactResolve ->
            if (artifactResolve.request.moduleVersionIdentifier.version.endsWith('-CHANGING')) {
                artifactResolve.cacheFor(0, SECONDS)
            }
        } as Action)
    }
}

dependencies {
    compile group: "group", name: "projectA", version: "1-CHANGING"
}

task retrieve(type: Copy) {
    into 'build'
    from configurations.compile
}
"""

        when: "Version 1-CHANGING is published"
        def module = ivyRepo().module("group", "projectA", "1-CHANGING").publish()

        and: "Server handles requests"
        server.expectGet('/repo/group/projectA/1-CHANGING/ivy-1-CHANGING.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1-CHANGING/projectA-1-CHANGING.jar', module.jarFile)

        and: "We request 1-CHANGING"
        run 'retrieve'

        then: "Version 1-CHANGING jar is used"
        file('build').assertHasDescendants('projectA-1-CHANGING.jar')
        def jarFile = file('build/projectA-1-CHANGING.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when: "Module meta-data is changed and artifacts are modified"
        module.artifact([name: 'other'])
        module.publishWithChangedContent()

        and: "Server handles requests"
        server.resetExpectations()
        // Server will be hit to get updated versions
        server.expectGet('/repo/group/projectA/1-CHANGING/ivy-1-CHANGING.xml.sha1', module.sha1File(module.ivyFile))
        server.expectHeadThenGet('/repo/group/projectA/1-CHANGING/ivy-1-CHANGING.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1-CHANGING/projectA-1-CHANGING.jar.sha1', module.sha1File(module.jarFile))
        server.expectHeadThenGet('/repo/group/projectA/1-CHANGING/projectA-1-CHANGING.jar', module.jarFile)
        server.expectGet('/repo/group/projectA/1-CHANGING/other-1-CHANGING.jar', module.moduleDir.file('other-1-CHANGING.jar'))

        and: "We request 1-CHANGING again"
        executer.withArguments()
        run 'retrieve'

        then: "We get new artifacts based on the new meta-data"
        file('build').assertHasDescendants('projectA-1-CHANGING.jar', 'other-1-CHANGING.jar')
        jarFile.assertHasChangedSince(snapshot)
        jarFile.assertIsCopyOf(module.jarFile)
    }

    def "avoid redownload unchanged artifact when no checksum available"() {
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
        def module = ivyRepo().module("group", "projectA", "1.1").publish()
        
        // Set the last modified to something that's not going to be anything “else”.
        // There are lots of dates floating around in a resolution and we want to make
        // sure we use this.
        module.jarFile.setLastModified(2000)
        module.ivyFile.setLastModified(6000)

        def base = "/repo/group/projectA/1.1"
        def ivyPath = "$base/$module.ivyFile.name"
        def ivySha1Path = "${ivyPath}.sha1"
        def originalIvyLastMod = module.ivyFile.lastModified()
        def originalIvyContentLength = module.ivyFile.length()
        def jarPath = "$base/$module.jarFile.name"
        def jarSha1Path = "${jarPath}.sha1"
        def originalJarLastMod = module.jarFile.lastModified()
        def originalJarContentLength = module.jarFile.length()

        when:
        server.expectGet(ivyPath, module.ivyFile)
        server.expectGet(jarPath, module.jarFile)

        run 'retrieve'

        then:
        def downloadedJar = file('build/projectA-1.1.jar')
        downloadedJar.assertIsCopyOf(module.jarFile)
        def snapshot = downloadedJar.snapshot()

        // Do change the jar, so we can check that the new version wasn't downloaded
        module.publishWithChangedContent()

        when:
        server.resetExpectations()
        server.expectHead(ivyPath, module.ivyFile, originalIvyLastMod, originalIvyContentLength)
        server.expectHead(jarPath, module.jarFile, originalJarLastMod, originalJarContentLength)

        run 'retrieve'

        then:
        downloadedJar.assertHasNotChangedSince(snapshot)

        when:
        server.resetExpectations()
        server.expectGetMissing(ivySha1Path)
        server.expectHead(ivyPath, module.ivyFile)
        server.expectGet(ivyPath, module.ivyFile)
        server.expectGetMissing(jarSha1Path)
        server.expectHead(jarPath, module.jarFile)
        server.expectGet(jarPath, module.jarFile)

        run 'retrieve'

        then:
        downloadedJar.assertHasChangedSince(snapshot)
        downloadedJar.assertIsCopyOf(module.jarFile)
    }
}
