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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.Repository
import org.gradle.test.fixtures.encoding.Identifier
import org.gradle.test.fixtures.server.http.IvyHttpModule
import spock.lang.Issue
import spock.lang.Unroll

class IvyDynamicRevisionRemoteResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'test' "

        resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()
    }

    @Issue("GRADLE-3264")
    def "resolves latest.milestone from when same dependency has a range constraint transitively"() {
        given:
        useRepository ivyHttpRepo

        buildFile << """
configurations { compile }

dependencies {
    compile group: "group", name: "projectA", version: "1.+"
}
"""
        when:
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.0.0").
                dependsOn("group", "projectB", "[1.0,1.2)").
                dependsOn("group", "projectC", "1.0.0").
                publish()
        def projectB11 = ivyHttpRepo.module("group", "projectB", "1.1").withStatus("milestone").publish()
        def projectB12 = ivyHttpRepo.module("group", "projectB", "1.2").withStatus("milestone").publish()

        def projectC1 = ivyHttpRepo.module("group", "projectC", "1.0.0").dependsOn("group", "projectB", "latest.milestone").publish()

        and:
        expectGetDynamicRevision(projectA1)
        expectGetDynamicRevision(projectB12)
        projectB11.ivy.expectGet()
        projectC1.ivy.expectGet()
        projectC1.jar.expectGet()

        then:
        assert succeeds('checkDeps')
    }

    def "uses latest version from version range and latest status"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
if (project.hasProperty('refreshDynamicVersions')) {
    configurations.all {
        resolutionStrategy.cacheDynamicVersionsFor 0, "seconds"
    }
}
dependencies {
    compile group: "group", name: "projectA", version: "1.+"
    compile group: "group", name: "projectB", version: "latest.integration"
}
"""
        when:
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        ivyHttpRepo.module("group", "projectA", "2.0").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()

        and:
        expectGetDynamicRevision(projectA1)
        expectGetDynamicRevision(projectB1)

        then:
        checkResolve "group:projectA:1.+": ["group:projectA:1.1", "didn't match version 2.0"],
                     "group:projectB:latest.integration": "group:projectB:1.1"

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()
        def projectB2 = ivyHttpRepo.module("group", "projectB", "2.2").publish()

        and:
        server.resetExpectations()
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB2)

        then:
        executer.withArgument("-PrefreshDynamicVersions")
        checkResolve "group:projectA:1.+": ["group:projectA:1.2", "didn't match version 2.0"],
                "group:projectB:latest.integration": "group:projectB:2.2"
    }

    @Unroll
    def "uses latest version from version range with #identifier characters"() {
        given:
        def name = identifier.safeForFileName().decorate("name")
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor 0, "seconds"
}

dependencies {
    compile group: /${name}/, name: /${name}/, version: "latest.integration"
}
"""
        when:
        def projectA1 = ivyHttpRepo.module(name, name, name).publish()

        and:
        expectGetDynamicRevision(projectA1)

        then:
        succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                dependency(group: name, module: name, version: 'latest.integration').selects(group: name, module: name, version: name)
            }
        }

        where:
        identifier << Identifier.all
    }

    def "determines latest version with jar only"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
  compile group: "group", name: "projectA", version: "1.+"
  compile group: "group", name: "projectB", version: "latest.integration"
}
"""

        when:
        ivyHttpRepo.module("group", "projectA", "1.1").withNoMetaData().publish()
        def projectA12 = ivyHttpRepo.module("group", "projectA", "1.2").withNoMetaData().publish()
        ivyHttpRepo.module("group", "projectA", "2.0").withNoMetaData().publish()
        ivyHttpRepo.module("group", "projectB", "1.1").withNoMetaData().publish()
        def projectB12 = ivyHttpRepo.module("group", "projectB", "1.2").withNoMetaData().publish()

        and:
        ivyHttpRepo.directoryList("group", "projectA").expectGet()
        projectA12.ivy.expectGetMissing()
        projectA12.jar.expectHead()
        projectA12.jar.expectGet()
        ivyHttpRepo.directoryList("group", "projectB").expectGet()
        projectB12.ivy.expectGetMissing()
        projectB12.jar.expectHead()
        projectB12.jar.expectGet()

        then:
        checkResolve "group:projectA:1.+": ["group:projectA:1.2", "didn't match version 2.0"],
                     "group:projectB:latest.integration": "group:projectB:1.2"

        when: "result is cached"
        server.resetExpectations()

        then:
        checkResolve "group:projectA:1.+": ["group:projectA:1.2", "didn't match version 2.0"],
                     "group:projectB:latest.integration": "group:projectB:1.2"
    }

    def "uses latest version with correct status for latest.release and latest.milestone"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
def latestRevision = project.getProperty('latestRevision')
configurations { compile }

dependencies {
    compile group: "group", name: "projectA", version: "latest.\${latestRevision}"
}
"""

        when:
        ivyHttpRepo.module("group", "projectA", "1.0").withStatus('release').publish()
        ivyHttpRepo.module('group', 'projectA', '1.1').withStatus('milestone').publish()
        ivyHttpRepo.module('group', 'projectA', '1.2').withStatus('integration').publish()
        def release = ivyHttpRepo.module("group", "projectA", "2.0").withStatus('release').publish()
        def milestone = ivyHttpRepo.module('group', 'projectA', '2.1').withStatus('milestone').publish()
        def integration = ivyHttpRepo.module('group', 'projectA', '2.2').withStatus('integration').publish()

        and:
        ivyHttpRepo.directoryList("group", "projectA").expectGet()
        integration.ivy.expectGet()
        milestone.ivy.expectGet()
        release.ivy.expectGet()
        release.jar.expectGet()

        and:
        executer.withArgument('-PlatestRevision=release')

        then:
        checkResolve "group:projectA:latest.release": [ "group:projectA:2.0", "didn't match versions 2.2, 2.1"]

        when:
        server.resetExpectations()
        milestone.jar.expectGet()
        executer.withArgument('-PlatestRevision=milestone')

        then:
        checkResolve "group:projectA:latest.milestone": ["group:projectA:2.1", "didn't match version 2.2"]

        when:
        server.resetExpectations()
        executer.withArgument('-PlatestRevision=milestone')

        then:
        checkResolve "group:projectA:latest.milestone": ["group:projectA:2.1", "didn't match version 2.2"]
    }

    def "reuses cached meta-data when resolving latest.status"() {
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")

        given:
        useRepository repo1, repo2
        buildFile << """
configurations {
    staticVersions {
        // Force load the metadata
        resolutionStrategy.componentSelection.all { ComponentSelection s -> 
            if (s.metadata.status != 'release') { 
                s.reject('nope') 
            } 
        }
    }
    compile
}
dependencies {
    staticVersions group: "group", name: "projectA", version: "1.1"
    compile group: "group", name: "projectA", version: "latest.milestone"
}
task cache { doLast { configurations.staticVersions.files } }
"""

        and:
        def repo1ProjectA1 = repo1.module("group", "projectA", "1.1").withStatus("milestone").publish()
        def repo2ProjectA1 = repo2.module("group", "projectA", "1.1").withStatus("release").publishWithChangedContent()
        def repo2ProjectA2 = repo2.module("group", "projectA", "1.2").publish()
        repo1ProjectA1.ivy.expectGet()
        repo2ProjectA1.ivy.expectHead()
        repo2ProjectA1.ivy.sha1.expectGet()
        repo2ProjectA1.ivy.expectGet()
        repo2ProjectA1.jar.expectGet()
        succeeds "cache"

        when:
        repo1.directoryList("group", "projectA").expectGet()
        repo2.directoryList("group", "projectA").expectGet()
        repo2ProjectA2.ivy.expectGet()
        repo1ProjectA1.jar.expectHead()
        repo1ProjectA1.jar.sha1.expectGet()
        repo1ProjectA1.jar.expectGet()

        then:
        checkResolve "group:projectA:latest.milestone": ["group:projectA:1.1", "didn't match version 1.2"]

        when:
        server.resetExpectations()

        then:
        checkResolve "group:projectA:latest.milestone": ["group:projectA:1.1", "didn't match version 1.2"]
    }

    def "can use latest version from different remote repositories"() {
        def repo1 = ivyHttpRepo("ivy1")
        def repo2 = ivyHttpRepo("ivy2")

        given:
        useRepository repo1, repo2
        buildFile << """
    configurations { compile }
    dependencies {
        compile group: "group", name: "projectA", version: "latest.milestone"
    }
    """

        when:
        def version11 = repo1.module('group', 'projectA', '1.1').withStatus('milestone').publish()
        def version12 = repo2.module('group', 'projectA', '1.2').withStatus('integration').publish()

        and:
        expectGetDynamicRevision(version11)

        repo2.directoryList("group", "projectA").expectGet()
        version12.ivy.expectGet()

        then:
        checkResolve "group:projectA:latest.milestone": ["group:projectA:1.1", "didn't match version 1.2"]

        when:
        server.resetExpectations()

        then:
        checkResolve "group:projectA:latest.milestone": ["group:projectA:1.1", "didn't match version 1.2"]
    }

    def "checks new repositories before returning any cached value"() {
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
"""

        when:
        def projectA11 = repo1.module("group", "projectA", "1.1").publish()
        def projectA12 = repo2.module("group", "projectA", "1.2").publish()

        and:
        expectGetDynamicRevision(projectA11)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.1"

        when:
        server.resetExpectations()
        expectGetDynamicRevision(projectA12)

        then:
        executer.withArguments("-PaddRepo2")
        checkResolve "group:projectA:1.+": "group:projectA:1.2"

        when:
        server.resetExpectations()

        then:
        executer.withArguments("-PaddRepo2")
        checkResolve "group:projectA:1.+": "group:projectA:1.2"
    }

    def "fails on broken directory listing in subsequent resolution"() {
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")

        given:
        useRepository repo1, repo2
        buildFile << """
    configurations { compile }
    dependencies {
        compile group: "group", name: "projectA", version: "1.+"
    }
    """

        when:
        def projectA12 = repo1.module("group", "projectA", "1.2").publish()
        def projectA11 = repo2.module("group", "projectA", "1.1").publish()

        and: "projectA is broken in repo1"
        repo1.directoryList("group", "projectA").expectGetBroken()

        then:
        fails "checkDeps"
        failure.assertHasCause "Could not resolve group:projectA:1.+."
        failure.assertHasCause "Could not list versions"
        failure.assertHasCause "Could not GET '$repo1.uri/group/projectA/'"

        when:
        server.resetExpectations()
        expectGetDynamicRevision(projectA12)
        expectGetDynamicRevisionMetadata(projectA11)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2"
    }

    def "uses and caches latest of versions obtained from multiple HTTP repositories"() {
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def repo3 = ivyHttpRepo("repo3")

        given:
        useRepository repo1, repo2, repo3
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "1.+"
}
"""

        when:
        def projectA11 = repo1.module("group", "projectA", "1.1").publish()
        def projectA12 = repo3.module("group", "projectA", "1.2").publish()

        and:
        repo1.directoryList("group", "projectA").expectGet()
        // TODO Should not need to get this
        projectA11.ivy.expectGet()
        repo2.directoryList("group", "projectA").expectGet()
        expectGetDynamicRevision(projectA12)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2"

        when:
        server.resetExpectations()

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2"
    }

    def "reuses cached artifacts that match multiple dynamic versions"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "org.test", name: "projectA", version: "1.+"
    compile group: "org.test", name: "projectA", version: "latest.integration"
}
"""

        when:
        def projectA11 = ivyHttpRepo.module("org.test", "projectA", "1.1").publish()
        def projectA12 = ivyHttpRepo.module("org.test", "projectA", "1.2").publish()

        and:
        expectGetDynamicRevision(projectA12)

        then:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org.test:projectA:1.+", "org.test:projectA:1.2"
                edge "org.test:projectA:latest.integration", "org.test:projectA:1.2"
            }
        }

        when:
        server.resetExpectations()

        and:
        buildFile << """
dependencies {
    compile group: "org.test", name: "projectA", version: "[1.0,2.0)"
}
"""

        then:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org.test:projectA:1.+", "org.test:projectA:1.2"
                edge "org.test:projectA:latest.integration", "org.test:projectA:1.2"
                edge "org.test:projectA:[1.0,2.0)", "org.test:projectA:1.2"
            }
        }
    }

    @Issue("gradle/gradle#3019")
    def "should honour dynamic version cache expiry for subsequent resolutions in the same build"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { 
    fresh
    stale
}
configurations.fresh.resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'

dependencies {
    fresh group: "org.test", name: "projectA", version: "1.+"
    stale group: "org.test", name: "projectA", version: "1.+"
}

task resolveStaleThenFresh {
    doFirst {
        println 'stale:' + configurations.stale.collect { it.name } + ',fresh:' + configurations.fresh.collect { it.name }
    }
}
"""

        when:
        def projectA11 = ivyHttpRepo.module("org.test", "projectA", "1.1").publish()
        def projectA12 = ivyHttpRepo.module("org.test", "projectA", "1.2").publish()

        and:
        expectGetDynamicRevision(projectA12)

        then:
        succeeds "resolveStaleThenFresh"

        and:
        outputContains("stale:[projectA-1.2.jar],fresh:[projectA-1.2.jar]")

        when:
        def projectA13 = ivyHttpRepo.module("org.test", "projectA", "1.3").publish()
        server.resetExpectations()

        and:
        // Should get the newer version when resolving 'fresh'
        expectGetDynamicRevision(projectA13)

        then:
        succeeds "resolveStaleThenFresh"

        and:
        outputContains("stale:[projectA-1.2.jar],fresh:[projectA-1.3.jar]")
    }

    def "reuses cached version lists unless no matches"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "org.test", name: "projectA", version: "1.+"
}
"""

        when:
        ivyHttpRepo.module("org.test", "projectA", "1.1").publish()
        def projectA21 = ivyHttpRepo.module("org.test", "projectA", "2.1").publish()
        def projectA12 = ivyHttpRepo.module("org.test", "projectA", "1.2").publish()

        and:
        ivyHttpRepo.directoryList("org.test", "projectA").expectGet()
        projectA12.ivy.expectGet()
        projectA12.jar.expectGet()

        then:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.+", "org.test:projectA:1.2").byReason("didn't match version 2.1")
            }
        }

        when:
        server.resetExpectations()
        projectA21.ivy.expectGet()
        projectA21.jar.expectGet()

        and:
        buildFile << """
dependencies {
    compile group: "org.test", name: "projectA", version: "2.+"
}
"""

        then:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.+", "org.test:projectA:2.1").byConflictResolution("between versions 1.2 and 2.1")
                edge("org.test:projectA:2.+", "org.test:projectA:2.1").byConflictResolution("between versions 1.2 and 2.1")
            }
        }

        when:
        def projectA30 = ivyHttpRepo.module("org.test", "projectA", "3.0").publish()
        server.resetExpectations()
        ivyHttpRepo.directoryList("org.test", "projectA").expectGet()
        projectA30.ivy.expectGet()
        projectA30.jar.expectGet()

        and:
        buildFile << """
dependencies {
    compile group: "org.test", name: "projectA", version: "3.+"
}
"""

        then:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.+", "org.test:projectA:3.0").byConflictResolution("between versions 1.2, 2.1 and 3.0")
                edge("org.test:projectA:2.+", "org.test:projectA:3.0").byConflictResolution("between versions 1.2, 2.1 and 3.0")
                edge("org.test:projectA:3.+", "org.test:projectA:3.0").byConflictResolution("between versions 1.2, 2.1 and 3.0")
            }
        }
    }

    def "caches resolved revisions until cache expiry"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "1.+"
}
if (project.hasProperty('noDynamicRevisionCache')) {
    configurations.all {
        resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
    }
}
"""

        when: "Version 1.1 is published"
        def version1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()

        and:
        expectGetDynamicRevision(version1)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.1"

        when: "Version 1.2 is published"
        server.resetExpectations()
        def version2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()

        then: "Version 1.1 is still used, as the 1.+ -> 1.1 mapping is cached"
        checkResolve "group:projectA:1.+": "group:projectA:1.1"

        when: "zero expiry for dynamic revision cache"
        executer.withArguments("-PnoDynamicRevisionCache")

        and:
        expectGetDynamicRevision(version2)

        then: "Version 1.2 is used"
        checkResolve "group:projectA:1.+": "group:projectA:1.2"
    }

    def "uses and caches dynamic revisions for transitive dependencies"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "main", version: "1.0"
}

if (project.hasProperty('noDynamicRevisionCache')) {
    configurations.all {
        resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
    }
}
"""

        when:
        def mainProject = ivyHttpRepo.module("group", "main", "1.0")
        mainProject.dependsOn("group", "projectA", "1.+")
        mainProject.dependsOn("group", "projectB", "latest.integration")
        mainProject.publish()

        and:
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        def projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()

        and:
        mainProject.ivy.expectGet()
        mainProject.jar.expectGet()
        expectGetDynamicRevision(projectA1)
        expectGetDynamicRevision(projectB1)

        then:
        succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:main:1.0") {
                    edge("group:projectA:1.+", "group:projectA:1.1")
                    edge("group:projectB:latest.integration", "group:projectB:1.1")
                }
            }
        }

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()
        def projectB2 = ivyHttpRepo.module("group", "projectB", "2.2").publish()

        and:
        server.resetExpectations()

        then:
        succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:main:1.0") {
                    edge("group:projectA:1.+", "group:projectA:1.1")
                    edge("group:projectB:latest.integration", "group:projectB:1.1")
                }
            }
        }

        when: "Server handles requests"
        server.resetExpectations()
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB2)

        and: "DynamicRevisionCache is bypassed"
        executer.withArguments("-PnoDynamicRevisionCache")

        then:
        succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:main:1.0") {
                    edge("group:projectA:1.+", "group:projectA:1.2")
                    edge("group:projectB:latest.integration", "group:projectB:2.2")
                }
            }
        }
    }

    def "resolves dynamic version with 2 repositories where first repo results in 404 for directory listing"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleA = repo2.module('group', 'projectA').publish()

        and:
        useRepository repo1, repo2
        buildFile << """
configurations { compile }
dependencies {
    compile 'group:projectA:1.+'
}
"""

        when:
        repo1.directoryList("group", "projectA").expectGetMissing()
        expectGetDynamicRevision(moduleA)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.0"

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.0"
    }

    def "reuses cached artifacts across repository types"() {
        def ivyRepo = ivyHttpRepo('repo1')
        def mavenRepo = mavenHttpRepo('repo2')
        def ivyModule = ivyRepo.module("org.test", "a", "1.1").publish()
        def mavenModule = mavenRepo.module("org.test", "a", "1.1").publish()
        assert ivyModule.jarFile.bytes == mavenModule.artifactFile.bytes

        given:
        useRepository ivyRepo
        buildFile << """
configurations { compile }

dependencies {
    compile 'org.test:a:1+'
}
"""

        when:
        expectGetDynamicRevision(ivyModule)

        then:
        checkResolve "org.test:a:1+": "org.test:a:1.1"

        when:
        buildFile.text = """
repositories {
    maven { url '${mavenRepo.uri}' }
}

configurations { compile }

dependencies {
    compile 'org.test:a:[1.0,2.0)'
}
"""
        resolve.prepare()

        and:
        mavenRepo.getModuleMetaData("org.test", "a").expectGet()
        mavenModule.pom.expectGet()
        mavenModule.artifact.expectHead()
        mavenModule.artifact.sha1.expectGet()

        then:
        checkResolve "org.test:a:[1.0,2.0)": "org.test:a:1.1:runtime"
    }

    def "can resolve dynamic versions from repository with multiple ivy patterns"() {
        given:
        def repo1versions = [:]
        def repo1 = ivyHttpRepo("ivyRepo1")
        def repo2versions = [:]
        def repo2 = ivyHttpRepo("ivyRepo2")
        repo1versions.A1 = repo1.module('org.test', 'projectA', '1.1').publish()
        repo1versions.A2 = repo1.module('org.test', 'projectA', '1.2').publish()
        repo1versions.A3 = repo1.module('org.test', 'projectA', '1.3') // unpublished

        repo2versions.A1 = repo2.module('org.test', 'projectA', '1.1').publish()
        repo2versions.A3 = repo2.module('org.test', 'projectA', '1.3').publish()

        repo1versions.B1 = repo1.module('org.test', 'projectB', '1.1').withStatus("integration").publish()
        repo1versions.B2 = repo1.module('org.test', 'projectB', '1.2').withStatus("milestone").publish()
        repo1versions.B3 = repo1.module('org.test', 'projectB', '1.3') // unpublished

        repo2versions.B1 = repo2.module('org.test', 'projectB', '1.1').withStatus("milestone").publish()
        repo2versions.B3 = repo2.module('org.test', 'projectB', '1.3').withStatus("integration").publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "${repo1.uri}"
        ivyPattern "${repo2.uri}/[organisation]/[module]/[revision]/ivy-[revision].xml"
        artifactPattern "${repo2.uri}/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations { compile }
dependencies {
  compile 'org.test:projectA:1.+'
  compile 'org.test:projectB:latest.milestone'
}
"""

        when:
        repo1.directoryList("org.test", "projectA").expectGet()
        // TODO Should not be looking in repo1, since A3 was not included in the version listing
        repo1versions.A3.ivy.expectGetMissing()
        repo1versions.A3.jar.expectGetMissing()
        expectGetDynamicRevision(repo2versions.A3)

        and:
        // TODO Should not be looking in repo1, since B3 was not included in the version listing
        repo1versions.B3.ivy.expectGetMissing()
        repo2.directoryList("org.test", "projectB").expectGet()
        repo2versions.B3.ivy.expectGet()
        expectGetDynamicRevision(repo1versions.B2)

        then:
        checkResolve "org.test:projectA:1.+": "org.test:projectA:1.3",
                     "org.test:projectB:latest.milestone": ["org.test:projectB:1.2", "didn't match version 1.3"]

        when: "resolve a second time"
        server.resetExpectations()

        then:
        checkResolve "org.test:projectA:1.+": "org.test:projectA:1.3",
                     "org.test:projectB:latest.milestone": ["org.test:projectB:1.2", "didn't match version 1.3"]

    }

    def "versions are listed once only per resolve"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "main", version: "1.0"
    compile group: "group", name: "projectA", version: "latest.integration"
}
configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
}
"""

        when:
        def projectA0 = ivyHttpRepo.module("group", "projectA", "1.0").publish()
        def projectA1 = ivyHttpRepo.module("group", "projectA", "1.1").publish()
        def mainProject = ivyHttpRepo.module("group", "main", "1.0")
        mainProject.dependsOn("group", "projectA", "1.+")
        mainProject.publish()

        and:
        mainProject.ivy.expectGet()
        mainProject.jar.expectGet()
        ivyHttpRepo.directoryList("group", "projectA").expectGet()
        projectA1.ivy.expectGet()
        projectA1.jar.expectGet()

        then:
        succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:main:1.0") {
                    edge("group:projectA:1.+", "group:projectA:1.1")
                }
                edge("group:projectA:latest.integration", "group:projectA:1.1")
            }
        }

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()

        and:
        server.resetExpectations()
        ivyHttpRepo.directoryList("group", "projectA").expectGet()
        projectA2.ivy.expectGet()
        projectA2.jar.expectGet()

        then:
        succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                module("group:main:1.0") {
                    edge("group:projectA:1.+", "group:projectA:1.2")
                }
                edge("group:projectA:latest.integration", "group:projectA:1.2")
            }
        }
    }

    def "reports and recovers from no matching version for dynamic version"() {
        def repo2 = ivyHttpRepo("repo-2")

        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "2.+"
}
"""

        when:
        ivyHttpRepo.module("group", "projectA", "1.1").publish()
        ivyHttpRepo.module("group", "projectA", "1.2").publish()
        ivyHttpRepo.module("group", "projectA", "3.0").publish()
        def dirListRepo1 = ivyHttpRepo.directoryList("group", "projectA")
        dirListRepo1.expectGet()

        then:
        fails "checkDeps"
        failure.assertHasCause("""Could not find any version that matches group:projectA:2.+.
Versions that do not match:
  - 3.0
  - 1.2
  - 1.1
Searched in the following locations: ${dirListRepo1.uri}
Required by:
""")

        when:
        useRepository repo2
        repo2.module("group", "projectA", "3.0").publish()
        repo2.module("group", "projectA", "4.4").publish()
        def dirListRepo2 = repo2.directoryList("group", "projectA")

        and:
        server.resetExpectations()
        dirListRepo1.expectGet()
        dirListRepo2.expectGet()

        then:
        fails "checkDeps"
        failure.assertHasCause("""Could not find any version that matches group:projectA:2.+.
Versions that do not match:
  - 3.0
  - 1.2
  - 1.1
  - 4.4
Searched in the following locations:
  - ${dirListRepo1.uri}
  - ${dirListRepo2.uri}
Required by:
""")

        when:
        server.resetExpectations()
        dirListRepo1.expectGet()
        dirListRepo2.expectGet()

        then:
        fails "checkDeps"
        failure.assertHasCause("""Could not find any version that matches group:projectA:2.+.
Versions that do not match:
  - 3.0
  - 1.2
  - 1.1
  - 4.4
Searched in the following locations:
  - ${dirListRepo1.uri}
  - ${dirListRepo2.uri}
Required by:
""")

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "2.2").publish()

        and:
        server.resetExpectations()
        dirListRepo1.allowGet()
        dirListRepo2.allowGet()
        projectA2.ivy.expectGet()
        projectA2.jar.expectGet()

        then:
        checkResolve "group:projectA:2.+": ["group:projectA:2.2", "didn't match versions 3.0, 1.2, 1.1, 4.4"]
    }

    def "reports and recovers from missing directory available for dynamic version"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "2.+"
}
"""

        when: "no versions"
        def directoryList = ivyHttpRepo.directoryList("group", "projectA")
        directoryList.expectGetMissing()

        then:
        fails "checkDeps"
        failure.assertHasCause("""Could not find any matches for group:projectA:2.+ as no versions of group:projectA are available.
Searched in the following locations: ${directoryList.uri}
Required by:
""")

        when: "no versions"
        server.resetExpectations()
        directoryList.expectGetMissing()

        then:
        fails "checkDeps"
        failure.assertHasCause("""Could not find any matches for group:projectA:2.+ as no versions of group:projectA are available.
Searched in the following locations: ${directoryList.uri}
Required by:
""")

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "2.2").publish()

        and:
        server.resetExpectations()
        expectGetDynamicRevision(projectA2)

        then:
        checkResolve "group:projectA:2.+": "group:projectA:2.2"
    }

    def "reports and recovers from missing dynamic version when no repositories defined"() {
        given:
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "2.+"
}
"""

        expect:
        fails "checkDeps"
        failure.assertHasCause("Cannot resolve external dependency group:projectA:2.+ because no repositories are defined.")

        when:
        useRepository ivyHttpRepo
        def projectA2 = ivyHttpRepo.module("group", "projectA", "2.2").publish()
        expectGetDynamicRevision(projectA2)

        then:
        checkResolve "group:projectA:2.+": "group:projectA:2.2"
    }

    def "reports and recovers from broken directory available for dynamic version"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "2.+"
}
"""

        when: "no version > 2"
        def directoryList = ivyHttpRepo.directoryList("group", "projectA")
        directoryList.expectGetBroken()

        then:
        fails "checkDeps"
        failure.assertHasCause("Could not resolve group:projectA:2.+")
        failure.assertHasCause("Could not list versions using Ivy pattern '${ivyHttpRepo.ivyPattern}'.")
        failure.assertHasCause("Could not GET '${directoryList.uri}'. Received status code 500 from server")

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "2.2").publish()

        and:
        server.resetExpectations()
        expectGetDynamicRevision(projectA2)

        then:
        checkResolve "group:projectA:2.+": "group:projectA:2.2"
    }

    def "reports and recovers from missing module for dynamic version that requires meta-data"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "latest.release"
}
"""

        when:
        def directoryList = ivyHttpRepo.directoryList("group", "projectA")
        def projectA = ivyHttpRepo.module("group", "projectA", "1.2").withStatus("release").publish()
        directoryList.expectGet()
        projectA.ivy.expectGetMissing()
        projectA.jar.expectHeadMissing()

        then:
        fails "checkDeps"
        failure.assertHasCause("""Could not find any matches for group:projectA:latest.release as no versions of group:projectA are available.
Searched in the following locations:
  - ${directoryList.uri}
  - ${projectA.ivy.uri}
  - ${projectA.jar.uri}
Required by:
""")

        when:
        server.resetExpectations()
        projectA.ivy.expectGet()
        projectA.jar.expectGet()

        then:
        checkResolve "group:projectA:latest.release": "group:projectA:1.2"
    }

    def "reports and recovers from broken module for dynamic version that requires meta-data"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "latest.release"
}
"""

        when:
        def directoryList = ivyHttpRepo.directoryList("group", "projectA")
        def projectA = ivyHttpRepo.module("group", "projectA", "1.2").withStatus("release").publish()
        directoryList.expectGet()
        projectA.ivy.expectGetBroken()

        then:
        fails "checkDeps"
        failure.assertHasCause("Could not resolve group:projectA:latest.release")
        failure.assertHasCause("Could not GET '${projectA.ivy.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        projectA.ivy.expectGet()
        projectA.jar.expectGetBroken()

        then:
        fails "checkDeps"
        failure.assertHasCause("Could not download projectA.jar (group:projectA:1.2)")
        failure.assertHasCause("Could not GET '${projectA.jar.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        projectA.jar.expectGet()

        then:
        checkResolve "group:projectA:latest.release": "group:projectA:1.2"
    }

    @Unroll
    def "finds best matching version in local and remote repository with #order"() {
        given:
        def fileRepo = ivyRepo("fileRepo")
        fileRepo.module('group', 'projectB', '1.1').publish()
        fileRepo.module('group', 'projectC', '1.1').publish()
        def httpModuleA = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        def httpModuleC = ivyHttpRepo.module('group', 'projectC', '1.2').publish()

        and:
        if (localFirst) {
            useRepository fileRepo, ivyHttpRepo
        } else {
            useRepository ivyHttpRepo, fileRepo
        }
        buildFile << """
configurations { compile }
dependencies {
    compile "group:\$moduleName:1.+"
}
configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
}
"""
        when: "missing from local"
        expectGetDynamicRevision(httpModuleA)

        then:
        args '-PmoduleName=projectA'
        checkResolve "group:projectA:1.+": "group:projectA:1.2"

        when: "missing from remote"
        ivyHttpRepo.directoryList("group", "projectB").expectGetMissing()

        then:
        args '-PmoduleName=projectB'
        checkResolve "group:projectB:1.+": "group:projectB:1.1"

        when: "present in both"
        server.resetExpectations()
        expectGetDynamicRevision(httpModuleC)

        then:
        args '-PmoduleName=projectC'
        checkResolve "group:projectC:1.+": "group:projectC:1.2"

        where:
        order          | localFirst
        "local first"  | true
        "remote first" | false
    }

    def "fails with reasonable error message when no cached version list in offline mode"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile 'group:projectA:1.+'
}
"""
        when:
        executer.withArgument "--offline"

        then:
        fails "checkDeps"
        failure.assertHasCause "Could not resolve all dependencies for configuration ':compile'."
        failure.assertHasCause "Could not resolve group:projectA:1.+."
        failure.assertHasCause "No cached version listing for group:projectA:1.+ available for offline mode."
    }

    def checkResolve(Map edges) {
        assert succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                edges.each {from, to ->
                    if (to instanceof List) {
                        edge(from, to[0]).byReason(to[1])
                    } else {
                        edge(from, to)
                    }
                }
            }
        }
        true
    }

    def expectGetDynamicRevision(IvyHttpModule module) {
        expectListVersions(module)
        module.ivy.expectGet()
        module.jar.expectGet()
    }

    def expectGetDynamicRevisionMetadata(IvyHttpModule module) {
        expectListVersions(module)
        module.ivy.expectGet()
    }

    private expectListVersions(IvyHttpModule module) {
        module.repository.directoryList(module.organisation, module.module).expectGet()
    }

    def expectGetStatusOf(IvyHttpModule module, String status = 'release') {
        def file = temporaryFolder.createFile("cheap-${module.version}.status")
        file << status
        server.expectGet("/repo/${module.organisation}/${module.module}/${module.version}/status.txt", file)
    }

    def useRepository(Repository... repo) {
        buildFile << """
repositories {
"""
        repo.each {
            buildFile << "ivy { url '${it.uri}' }\n"
        }
        buildFile << """
}
"""
    }

}
