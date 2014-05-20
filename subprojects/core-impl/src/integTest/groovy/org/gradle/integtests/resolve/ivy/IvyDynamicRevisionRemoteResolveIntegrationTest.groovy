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
import org.gradle.integtests.resolve.ResolveTestFixture
import org.gradle.test.fixtures.Repository
import org.gradle.test.fixtures.encoding.Identifier
import org.gradle.test.fixtures.server.http.IvyHttpModule
import spock.lang.Unroll

class IvyDynamicRevisionRemoteResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'test' "

        resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()
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
        checkResolve "group:projectA:1.+": "group:projectA:1.1",
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
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.integration": "group:projectB:2.2"
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
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        projectA12.ivy.expectGetMissing()
        projectA12.jar.expectHead()
        projectA12.jar.expectGet()
        ivyHttpRepo.expectDirectoryListGet("group", "projectB")
        projectB12.ivy.expectGetMissing()
        projectB12.jar.expectHead()
        projectB12.jar.expectGet()

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2",
                     "group:projectB:latest.integration": "group:projectB:1.2"

        when: "result is cached"
        server.resetExpectations()

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2",
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
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
        integration.ivy.expectGet()
        milestone.ivy.expectGet()
        release.ivy.expectGet()
        release.jar.expectGet()

        and:
        executer.withArgument('-PlatestRevision=release')

        then:
        checkResolve "group:projectA:latest.release": "group:projectA:2.0"

        when:
        server.resetExpectations()
        milestone.jar.expectGet()
        executer.withArgument('-PlatestRevision=milestone')

        then:
        checkResolve "group:projectA:latest.milestone": "group:projectA:2.1"

        when:
        server.resetExpectations()
        executer.withArgument('-PlatestRevision=milestone')

        then:
        checkResolve "group:projectA:latest.milestone": "group:projectA:2.1"
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

        repo2.expectDirectoryListGet("group", "projectA")
        version12.ivy.expectGet()

        then:
        checkResolve "group:projectA:latest.milestone": "group:projectA:1.1"

        when:
        server.resetExpectations()

        then:
        checkResolve "group:projectA:latest.milestone": "group:projectA:1.1"
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

    def "recovers from broken modules in subsequent resolution"() {
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
        repo1.expectDirectoryListGetBroken("group", "projectA")
        expectGetDynamicRevision(projectA11)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.1"

        when:
        server.resetExpectations()
        expectGetDynamicRevision(projectA12)

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
        repo1.expectDirectoryListGet("group", "projectA")
        // TODO Should not need to get this
        projectA11.ivy.expectGet()
        repo2.expectDirectoryListGet("group", "projectA")
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
        ivyHttpRepo.module("org.test", "projectA", "1.1").publish()
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

    public void "resolves dynamic version with 2 repositories where first repo results in 404 for directory listing"() {
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
        repo1.expectDirectoryListGetMissing("group", "projectA")
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
        checkResolve "org.test:a:[1.0,2.0)": "org.test:a:1.1"
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
        repo1.expectDirectoryListGet("org.test", "projectA")
        // TODO Should not be looking in repo1, since A3 was not included in the version listing
        repo1versions.A3.ivy.expectGetMissing()
        repo1versions.A3.jar.expectGetMissing()
        expectGetDynamicRevision(repo2versions.A3)

        and:
        // TODO Should not be looking in repo1, since B3 was not included in the version listing
        repo1versions.B3.ivy.expectGetMissing()
        repo2.expectDirectoryListGet("org.test", "projectB")
        repo2versions.B3.ivy.expectGet()
        expectGetDynamicRevision(repo1versions.B2)

        then:
        checkResolve "org.test:projectA:1.+": "org.test:projectA:1.3",
                     "org.test:projectB:latest.milestone": "org.test:projectB:1.2"

        when: "resolve a second time"
        server.resetExpectations()

        then:
        checkResolve "org.test:projectA:1.+": "org.test:projectA:1.3",
                     "org.test:projectB:latest.milestone": "org.test:projectB:1.2"

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
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
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
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")
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

    def "reports and recovers from no matching dynamic version"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "2.+"
}
"""

        when: "no version > 2"
        ivyHttpRepo.module("group", "projectA", "1.1").publish()
        ivyHttpRepo.expectDirectoryListGet("group", "projectA")

        then:
        fails "checkDeps"
        failure.assertHasCause("Could not find any version that matches group:projectA:2.+.")

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "2.2").publish()

        and:
        server.resetExpectations()
        expectGetDynamicRevision(projectA2)

        then:
        checkResolve "group:projectA:2.+": "group:projectA:2.2"
    }

    def "reports and recovers from no versions available for dynamic version"() {
        given:
        useRepository ivyHttpRepo
        buildFile << """
configurations { compile }
dependencies {
    compile group: "group", name: "projectA", version: "2.+"
}
"""

        when: "no version > 2"
        ivyHttpRepo.expectDirectoryListGetMissing("group", "projectA")

        then:
        fails "checkDeps"
        failure.assertHasCause("Could not find any version that matches group:projectA:2.+.")

        when:
        def projectA2 = ivyHttpRepo.module("group", "projectA", "2.2").publish()

        and:
        server.resetExpectations()
        expectGetDynamicRevision(projectA2)

        then:
        checkResolve "group:projectA:2.+": "group:projectA:2.2"
    }

    @Unroll
    def "finds best matching version in local and remote repository with #order"() {
        given:
        def fileRepo = ivyRepo("fileRepo")
        def httpModule = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        and:
        if (localFirst) {
            useRepository fileRepo, ivyHttpRepo
        } else {
            useRepository ivyHttpRepo, fileRepo
        }
        buildFile << """
configurations { compile }
dependencies {
    compile 'group:projectA:1.+'
}
configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
}
"""
        when: "missing from local"
        expectGetDynamicRevision(httpModule)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2"

        when: "missing from remote"
        fileRepo.module('group', 'projectA', '1.1').publish()
        ivyHttpRepo.expectDirectoryListGetMissing("group", "projectA")

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.1"

        when: "present in both"
        server.resetExpectations()
        httpModule = ivyHttpRepo.module('group', 'projectA', '1.3').publish()
        expectGetDynamicRevision(httpModule)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.3"

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
        failure.assertHasCause "No cached version listing for group:projectA:1.+ available for offline mode."
    }

    def checkResolve(Map edges) {
        assert succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                edges.each {from, to ->
                    edge(from, to)
                }
            }
        }
        true
    }

    def expectGetDynamicRevision(IvyHttpModule module) {
        module.repository.expectDirectoryListGet(module.organisation, module.module)
        module.ivy.expectGet()
        module.jar.expectGet()
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
