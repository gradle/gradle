/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.RepositoryServer
import org.junit.Rule

@LeaksFileHandles
abstract class AbstractIvyRemoteRepoResolveIntegrationTest extends AbstractIntegrationSpec {

    abstract RepositoryServer getServer()

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    @Rule
    ProgressLoggingFixture progressLogger = new ProgressLoggingFixture(executer, temporaryFolder)

    void "can resolve dependencies from a remote Ivy repository with #layout layout"() {
        given:
        def remoteIvyRepo = server.getRemoteIvyRepo(m2Compatible, null, ivyFilePattern, artifactFilePattern)
        def module = remoteIvyRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${remoteIvyRepo.uri}"
                    $server.validCredentials
                    layout '$layout'
                }
            }
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.2' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:
        module.ivy.expectDownload()
        module.jar.expectDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'

        where:
        layout   | m2Compatible | ivyFilePattern             | artifactFilePattern
        'gradle' | false        | 'ivy-[revision].xml'       | '[artifact]-[revision](.[ext])'
        'maven'  | true         | 'ivy-[revision].xml'       | '[artifact]-[revision](.[ext])'
        'ivy'    | false        | '[type]s/[artifact].[ext]' | '[type]s/[artifact].[ext]'
    }

    void "can resolve dependencies from a remote Ivy repository with pattern layout and m2compatible: #m2Compatible"() {
        given:
        def remoteIvyRepo = server.getRemoteIvyRepo(m2Compatible, "[module]/[organisation]/[revision]")
        def module = remoteIvyRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${remoteIvyRepo.uri}"
                    $server.validCredentials
                    patternLayout {
                        artifact "${remoteIvyRepo.baseArtifactPattern}"
                        ivy "${remoteIvyRepo.baseIvyPattern}"
                        m2compatible = $m2Compatible
                    }
                }
            }
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.2' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:
        module.ivy.expectDownload()
        module.jar.expectDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'

        where:
        m2Compatible << [false, true]
    }

    void "can resolve dependencies from a remote Ivy repository with multiple patterns configured"() {
        given:
        def emptyRepo = server.getRemoteIvyRepo('/empty')
        def thirdPartyModuleInEmptyRepo = emptyRepo.module('other', '3rdParty', '1.2')
        def companyModuleInEmptyRepo = emptyRepo.module('company', 'original', '1.1')

        def thirdPartyIvyRepo = server.getRemoteIvyRepo(false, "third-party/[organisation]/[module]/[revision]")
        def thirdPartyModule = thirdPartyIvyRepo.module('other', '3rdParty', '1.2')
        thirdPartyModule.publish()
        def companyModuleInThirdPartyRepo = thirdPartyIvyRepo.module('company', 'original', '1.1')

        and:
        def companyIvyRepo = server.getRemoteIvyRepo(false, "company/[module]/[revision]")
        def companyModule = companyIvyRepo.module('company', 'original', '1.1')
        companyModule.publish()


        and:
        buildFile << """
            repositories {
                ivy {
                    $server.validCredentials
                    url "${emptyRepo.uri}"
                    artifactPattern "${thirdPartyIvyRepo.artifactPattern}"
                    artifactPattern "${companyIvyRepo.artifactPattern}"
                    ivyPattern "${thirdPartyIvyRepo.ivyPattern}"
                    ivyPattern "${companyIvyRepo.ivyPattern}"
                }
            }
            configurations { compile }
            dependencies {
                compile 'other:3rdParty:1.2', 'company:original:1.1'
            }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """
        println buildFile.text

        when:
        thirdPartyModuleInEmptyRepo.ivy.expectDownloadMissing()
        thirdPartyModuleInEmptyRepo.jar.expectDownloadMissing()
        thirdPartyModule.ivy.expectDownload()
        thirdPartyModule.jar.expectDownload()

        companyModuleInEmptyRepo.ivy.expectDownloadMissing()
        companyModuleInEmptyRepo.jar.expectDownloadMissing()
        companyModuleInThirdPartyRepo.ivy.expectDownloadMissing()
        companyModuleInThirdPartyRepo.jar.expectDownloadMissing()
        companyModule.ivy.expectDownload()
        companyModule.jar.expectDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants '3rdParty-1.2.jar', 'original-1.1.jar'
    }

    public void "can resolve and cache dependencies from multiple remote Ivy repositories"() {
        given:
        def repo1 = server.getRemoteIvyRepo("/repo1")
        def repo2 = server.getRemoteIvyRepo("/repo2")
        def moduleA = repo1.module('group', 'projectA')
        moduleA.publish()
        def missingModuleB = repo1.module('group', 'projectB')
        def moduleB = repo2.module('group', 'projectB')
        moduleB.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${repo1.uri}"
                    $server.validCredentials
                }
                ivy {
                    url "${repo2.uri}"
                    $server.validCredentials
                }
            }
            configurations {
                compile {
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
            }
            dependencies {
                compile 'group:projectA:1.0', 'group:projectB:1.0'
            }
            task listJars {
                def compileConfig = configurations.compile
                doLast {
                    assert compileConfig.collect { it.name } == ['projectA-1.0.jar', 'projectB-1.0.jar']
                }
            }
        """

        when:
        moduleA.ivy.expectDownload()
        moduleA.jar.expectDownload()

        // Handles missing in repo1
        missingModuleB.ivy.expectDownloadMissing()

        moduleB.ivy.expectDownload()
        moduleB.jar.expectDownload()


        then:
        succeeds('listJars')

        when:
        server.resetExpectations()

        then:
        succeeds('listJars')
    }

    public void "can resolve and cache dependencies from a remote Ivy repository"() {
        given:
        def module = server.remoteIvyRepo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${server.remoteIvyRepo.uri}"
                    $server.validCredentials
                }
            }
            configurations {
                compile {
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
            }
            dependencies { compile 'group:projectA:1.2' }
            task listJars {
                def compileConfig = configurations.compile
                doLast {
                    assert compileConfig.collect { it.name } == ['projectA-1.2.jar']
                }
            }
        """
        when:
        module.ivy.expectDownload()
        module.jar.expectDownload()

        then:
        succeeds 'listJars'
        progressLogger.downloadProgressLogged(module.ivy.uri)
        progressLogger.downloadProgressLogged(module.jar.uri)

        when:
        server.resetExpectations()

        then:
        succeeds 'listJars'
    }

    void "can resolve and cache artifact-only dependencies from a remote Ivy repository"() {
        given:
        def module = server.remoteIvyRepo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${server.remoteIvyRepo.uri}"
                    $server.validCredentials
                }
            }
            configurations {
                compile {
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
            }
            dependencies { compile 'group:projectA:1.2@jar' }
            task listJars {
                def compileConfig = configurations.compile
                doLast {
                    assert compileConfig.collect { it.name } == ['projectA-1.2.jar']
                }
            }
        """


        when:
        module.ivy.expectDownload()
        module.jar.expectDownload()

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    def "can resolve and cache artifact-only dependencies with no descriptor from a remote Ivy repository"() {
        given:
        def module = server.remoteIvyRepo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${server.remoteIvyRepo.uri}"
                    $server.validCredentials
                    metadataSources { artifact() }
                }
            }
            configurations {
                compile {
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
            }
            dependencies { compile 'group:projectA:1.2@jar' }
            task listJars {
                def compileConfig = configurations.compile
                doLast {
                    assert compileConfig.collect { it.name } == ['projectA-1.2.jar']
                }
            }
        """


        when:
        module.jar.expectMetadataRetrieve()
        module.jar.expectDownload()

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    @ToBeFixedForConfigurationCache(
        skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP,
        because = "IvyGcsRepoResolveIntegrationTest leaks test files"
    )
    def "reuses cached details when switching ivy resolve mode"() {
        given:
        buildFile << """
            configurations {
                compile {
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
            }
            dependencies {
                repositories {
                    ivy {
                        url "${server.remoteIvyRepo.uri}"
                        $server.validCredentials
                        resolve.dynamicMode = project.hasProperty('useDynamicResolve')
                    }
                }
                compile 'org:projectA:1.2'
            }
            task retrieve(type: Sync) {
              from configurations.compile
              into 'libs'
            }
        """
        def moduleA = server.remoteIvyRepo.module('org', 'projectA', '1.2')
        moduleA.dependsOn(organisation: 'org', module: 'projectB', revision: '1.5', revConstraint: 'latest.integration')
            .publish()

        def moduleB15 = server.remoteIvyRepo.module('org', 'projectB', '1.5')
        moduleB15.publish()

        def moduleB16 = server.remoteIvyRepo.module('org', 'projectB', '1.6')
        moduleB16.publish()

        when:
        moduleA.ivy.expectDownload()
        moduleA.jar.expectDownload()
        moduleB15.ivy.expectDownload()
        moduleB15.jar.expectDownload()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar')

        when:
        server.resetExpectations()
        server.remoteIvyRepo.directoryList('org', 'projectB').expectDownload()
        moduleB16.ivy.expectDownload()
        moduleB16.jar.expectDownload()
        executer.withArguments("-PuseDynamicResolve=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.6.jar')

        when:
        server.resetExpectations()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar')

        when:
        server.resetExpectations()
        executer.withArguments("-PuseDynamicResolve=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.6.jar')
    }
}
