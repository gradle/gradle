/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.HttpResource
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

class DependencyUnresolvedModuleIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private static final String GROUP_ID = 'group'
    private static final String VERSION = '1.0'
    TestFile downloadedLibsDir
    MavenHttpModule moduleA

    def setup() {
        moduleA = publishMavenModule(mavenHttpRepo, 'a')
        downloadedLibsDir = file('build/libs')
        executer.beforeExecute {
            executer.withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=1000")
        }
    }

    void blockingForProtocol(String protocol, HttpResource... resources) {
        if (protocol == 'http') {
            resources.each { it.expectGetBlocking() }
        } else if (protocol == 'https') {
            // https://issues.apache.org/jira/browse/HTTPCLIENT-1478
            def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
            keyStore.enableSslWithServerCert(server)
            keyStore.configureServerCert(executer)
            server.expectSslHandshakeBlocking()
        } else {
            assert false: "Unsupported protocol: ${protocol}"
        }
    }

    def "fails single build script dependency resolution if #protocol connection exceeds timeout"() {
        given:
        blockingForProtocol(protocol, moduleA.pom)
        buildFile << """
            buildscript {
                ${mavenRepository(mavenHttpRepo)}

                dependencies {
                    classpath '${mavenModuleCoordinates(moduleA)}'
                }
            }
        """

        when:
        fails('help')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)

        where:
        protocol << ['http', 'https']
    }

    def "fails single application dependency resolution if #protocol connection exceeds timeout (retries = #maxRetries)"() {
        maxHttpRetries = maxRetries

        given:
        maxHttpRetries.times {
            blockingForProtocol(protocol, moduleA.pom)
        }
        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        fails('resolve')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)
        !downloadedLibsDir.isDirectory()

        where:
        protocol | maxRetries
        'http'   | 1
        'https'  | 1
        'http'   | 2
        'https'  | 2
    }

    def "fails concurrent application dependency resolution if #protocol connection exceeds timeout"() {
        given:
        MavenHttpModule moduleB = publishMavenModule(mavenHttpRepo, 'b')
        MavenHttpModule moduleC = publishMavenModule(mavenHttpRepo, 'c')
        blockingForProtocol(protocol, moduleA.pom, moduleB.pom, moduleC.pom)

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA, moduleB, moduleC)}
            ${configSyncTask()}
        """

        when:
        fails('resolve', '--max-workers=3')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)
        assertDependencyMetaDataReadTimeout(moduleB)
        assertDependencyMetaDataReadTimeout(moduleC)
        !downloadedLibsDir.isDirectory()
        where:
        protocol << ['http', 'https']
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "prevents using repository in later resolution within the same build on HTTP timeout"() {
        given:
        MavenHttpModule moduleB = publishMavenModule(mavenHttpRepo, 'b')
        MavenHttpModule moduleC = publishMavenModule(mavenHttpRepo, 'c')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}

            configurations {
                first
                second
                third
            }
            dependencies {
                first '${mavenModuleCoordinates(moduleA)}'
                second '${mavenModuleCoordinates(moduleB)}'
                third '${mavenModuleCoordinates(moduleC)}'
            }

            task resolve {
                doLast {
                    def filesA = configurations.first.incoming.artifactView { lenient = true }.files.files*.name
                    def filesB = configurations.second.incoming.artifactView { lenient = true }.files.files*.name
                    def filesC = configurations.third.incoming.artifactView { lenient = true }.files.files*.name
                    println "Resolved: \${filesA} \${filesB} \${filesC}"
                }
            }
        """

        when:
        moduleA.pom.expectGet()
        moduleA.artifact.expectGet()
        moduleB.pom.expectGetBlocking()
        // No attempt made to get moduleC

        succeeds 'resolve'

        then:
        output.contains "Resolved: [a-1.0.jar] [] []"
    }

    def "repository is disabled only for the current build execution"() {
        given:

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGetBlocking()
        fails('resolve')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)
        !downloadedLibsDir.isDirectory()

        when:
        moduleA.pom.expectGet()
        moduleA.artifact.expectGet()
        succeeds('resolve')

        then:
        downloadedLibsDir.assertContainsDescendants('a-1.0.jar')
    }

    def "skips subsequent dependency resolution if HTTP connection exceeds timeout"() {
        given:
        MavenHttpModule moduleB = publishMavenModule(mavenHttpRepo, 'b')
        MavenHttpModule moduleC = publishMavenModule(mavenHttpRepo, 'c')
        def moduleD = mavenHttpRepo.module(GROUP_ID, 'd', VERSION).dependsOn(moduleA).publish()
        def moduleE = mavenHttpRepo.module(GROUP_ID, 'e', VERSION).dependsOn(moduleB).dependsOn(moduleC).publish()

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleD, moduleE)}
            ${configSyncTask()}
        """

        when:
        moduleD.pom.expectGet()
        moduleE.pom.expectGet()
        moduleA.pom.expectGetBlocking()
        fails('resolve', '--max-workers=1')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)
        assertDependencySkipped(moduleB)
        assertDependencySkipped(moduleC)
        !downloadedLibsDir.isDirectory()
    }

    def "fails build and #abortDescriptor repository search if HTTP connection #reason when resolving metadata"() {
        given:
        MavenHttpRepository backupMavenHttpRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))
        def backupA = publishMavenModule(backupMavenHttpRepo, 'a')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${mavenRepository(backupMavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom."$action"()
        if (!abort) {
            backupA.pom."${action}"()
        }
        fails('resolve')

        then:
        "$outcome"(moduleA)
        !downloadedLibsDir.isDirectory()

        where:
        reason                          | abort | action                  | outcome
        'exceeds timeout'               | true  | 'expectGetBlocking'     | 'assertDependencyMetaDataReadTimeout'
        'returns internal server error' | true  | 'expectGetBroken'       | 'assertDependencyMetaDataInternalServerError'
        'returns uncritical error'      | false | 'expectGetUnauthorized' | 'assertDependencyMetaDataUnauthorizedError'

        abortDescriptor = abort ? 'aborts' : 'does not abort'
    }

    def "fails build and aborts repository search if HTTP connection #reason when resolving artifact for found module"() {
        given:
        MavenHttpRepository backupMavenHttpRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))
        publishMavenModule(backupMavenHttpRepo, 'a')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${mavenRepository(backupMavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGet()
        moduleA.artifact."$action"()
        fails('resolve')

        then:
        "$outcome"(moduleA)
        !downloadedLibsDir.isDirectory()

        where:
        reason                          | action                  | outcome
        'exceeds timeout'               | 'expectGetBlocking'     | 'assertDependencyArtifactReadTimeout'
        'returns internal server error' | 'expectGetBroken'       | 'assertDependencyArtifactInternalServerError'
        'returns uncritical error'      | 'expectGetUnauthorized' | 'assertDependencyArtifactUnauthorizedError'
    }

    def "fails build and #abortDescriptor repository search if HTTP connection #reason when resolving dynamic version"() {
        given:
        MavenHttpRepository backupMavenHttpRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))
        publishMavenModule(backupMavenHttpRepo, 'a')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${mavenRepository(backupMavenHttpRepo)}
            ${customConfigDependencyAssignment('group:a:1.+')}
            ${configSyncTask()}
        """

        when:
        mavenHttpRepo.getModuleMetaData('group', 'a')."$action"()
        if (!abort) {
            backupMavenHttpRepo.getModuleMetaData('group', 'a')."$action"()
        }
        fails('resolve')

        then:
        "$outcome"('group', 'a', '1.+')
        !downloadedLibsDir.isDirectory()

        where:
        reason                          | abort | action                  | outcome
        'exceeds timeout'               | true  | 'expectGetBlocking'     | 'assertDependencyListingReadTimeout'
        'returns internal server error' | true  | 'expectGetBroken'       | 'assertDependencyListingInternalServerError'
        'returns uncritical error'      | false | 'expectGetUnauthorized' | 'assertDependencyListingUnauthorizedError'

        abortDescriptor = abort ? 'aborts' : 'does not abort'
    }

    private String mavenRepository(MavenRepository repo) {
        """
            repositories {
                maven { url "${repo.uri}"}
            }
        """
    }

    private String customConfigDependencyAssignment(String... modules) {
        """
            configurations {
                deps
            }

            dependencies {
                deps ${modules.collect { "'${it}'" }.join(', ')}
            }
        """
    }

    private String customConfigDependencyAssignment(MavenHttpModule... modules) {
        customConfigDependencyAssignment(modules.collect { "${mavenModuleCoordinates(it)}" } as String[])
    }

    private String configSyncTask() {
        """
            task resolve(type: Sync) {
                from configurations.deps
                into "\$buildDir/libs"
            }
        """
    }

    private void assertDependencyListingReadTimeout(String group, String module, String version) {
        failure.assertHasCause("Could not resolve ${group}:${module}:${version}.")
        failure.assertHasCause("Failed to list versions for ${group}:${module}.")
        failure.assertHasCause("Unable to load Maven meta-data from ${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'.")
        failure.assertHasCause("Read timed out")
    }

    private void assertDependencyListingInternalServerError(String group, String module, String version) {
        failure.assertHasCause("Could not resolve ${group}:${module}:${version}.")
        failure.assertHasCause("Failed to list versions for ${group}:${module}.")
        failure.assertHasCause("Unable to load Maven meta-data from ${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'. Received status code 500 from server: broken")
    }

    private void assertDependencyListingUnauthorizedError(String group, String module, String version) {
        failure.assertHasCause("Could not resolve ${group}:${module}:${version}.")
        failure.assertHasCause("Failed to list versions for ${group}:${module}.")
        failure.assertHasCause("Unable to load Maven meta-data from ${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'. Received status code 401 from server: Unauthorized")
    }

    private void assertDependencyMetaDataReadTimeout(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Read timed out")
    }

    private void assertDependencyMetaDataInternalServerError(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'. Received status code 500 from server: broken")
    }

    private void assertDependencyMetaDataUnauthorizedError(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'. Received status code 401 from server: Unauthorized")
    }

    private void assertDependencyArtifactReadTimeout(MavenModule module) {
        failure.assertHasCause("Could not download ${module.artifactFile.name} (${mavenModuleCoordinates(module)})")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Read timed out")
    }

    private void assertDependencyArtifactInternalServerError(MavenModule module) {
        failure.assertHasCause("Could not download ${module.artifactFile.name} (${mavenModuleCoordinates(module)})")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'. Received status code 500 from server: broken")
    }

    private void assertDependencyArtifactUnauthorizedError(MavenModule module) {
        failure.assertHasCause("Could not download ${module.artifactFile.name} (${mavenModuleCoordinates(module)})")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'. Received status code 401 from server: Unauthorized")
    }

    private void assertDependencySkipped(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Skipped due to earlier error")
    }

    private String mavenModuleCoordinates(MavenHttpModule module) {
        "$module.groupId:$module.artifactId:$module.version"
    }

    private String mavenModuleRepositoryPath(MavenHttpModule module) {
        "$module.groupId/$module.artifactId/$module.version/$module.artifactId-$module.version"
    }

    private MavenHttpModule publishMavenModule(MavenHttpRepository mavenHttpRepo, String artifactId) {
        mavenHttpRepo.module(GROUP_ID, artifactId, VERSION).publish()
    }
}
