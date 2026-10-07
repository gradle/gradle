/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule

import static org.gradle.util.Matchers.containsText

class RepositoryFailureScopeIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Rule
    HttpServer plainServer = new HttpServer()

    MavenHttpRepository backupRepo

    def setup() {
        backupRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))
    }


    def "module missing from the first repository does not stop the search"() {
        given:
        def first = mavenHttpRepo.module('group', 'a', '1.0')
        def second = backupRepo.module('group', 'a', '1.0').publish()
        buildFile << twoRepositories() + resolveTask()

        when:
        first.pom.expectGetMissing()
        second.pom.expectGet()
        second.artifact.expectGet()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }

    def "unauthorized response from the first repository does not stop the search"() {
        given:
        def first = mavenHttpRepo.module('group', 'a', '1.0').publish()
        def second = backupRepo.module('group', 'a', '1.0').publish()
        buildFile << twoRepositories() + resolveTask()

        when:
        first.pom.expectGetUnauthorized()
        second.pom.expectGet()
        second.artifact.expectGet()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }

    def "unparseable POM in the first repository does not stop the search"() {
        given:
        def first = mavenHttpRepo.module('group', 'a', '1.0').publish()
        first.pomFile.text = "<project><artifactId>"
        def second = backupRepo.module('group', 'a', '1.0').publish()
        buildFile << twoRepositories() + resolveTask()

        when:
        first.pom.expectGet()
        second.allowAll()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }

    def "POM declaring the wrong coordinates does not stop the search"() {
        given:
        def first = mavenHttpRepo.module('group', 'a', '1.0').publish()
        first.pomFile.text = first.pomFile.text.replace('<artifactId>a</artifactId>', '<artifactId>somethingelse</artifactId>')
        def second = backupRepo.module('group', 'a', '1.0').publish()
        buildFile << twoRepositories() + resolveTask()

        when:
        first.pom.expectGet()
        second.allowAll()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }

    def "unparseable ivy descriptor in the first repository does not stop the search"() {
        given:
        def firstRepo = ivyHttpRepo('repo-ivy-1')
        def secondRepo = ivyHttpRepo('repo-ivy-2')
        def first = firstRepo.module('group', 'a', '1.0').publish()
        first.ivyFile.text = "<ivy-module><info>"
        def second = secondRepo.module('group', 'a', '1.0').publish()

        buildFile << """
            repositories {
                ivy { url = "${firstRepo.uri}" }
                ivy { url = "${secondRepo.uri}" }
            }
            ${resolveTask()}
        """

        when:
        first.ivy.expectGet()
        second.allowAll()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }

    def "unparseable Gradle module metadata in the first repository does not stop the search"() {
        given:
        def firstRepo = ivyHttpRepo('repo-ivy-1')
        def secondRepo = ivyHttpRepo('repo-ivy-2')
        def first = firstRepo.module('group', 'a', '1.0').withModuleMetadata().publish()
        first.moduleMetadataFile.text = "{ not json"
        def second = secondRepo.module('group', 'a', '1.0').publish()

        buildFile << """
            repositories {
                ivy { url = "${firstRepo.uri}" }
                ivy { url = "${secondRepo.uri}" }
            }
            ${resolveTask()}
        """

        when:
        first.ivy.expectGet()
        first.moduleMetadata.expectGet()
        second.allowAll()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }

    def "failure in a local repository does not stop the search"() {
        given:
        def local = mavenRepo.module('group', 'a', '1.0').publish()
        local.pomFile.text = "<project><artifactId>"
        def second = backupRepo.module('group', 'a', '1.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
                maven { url = "${backupRepo.uri}" }
            }
            ${resolveTask()}
        """

        when:
        second.pom.expectGet()
        second.artifact.expectGet()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }

    def "a request-specific failure leaves the repository serving other modules"() {
        given:
        // 'bad' is declared first, so it is resolved before 'good' is requested.
        def bad = mavenHttpRepo.module('group', 'bad', '1.0').publish()
        bad.pomFile.text = "<project><artifactId>"
        def good = mavenHttpRepo.module('group', 'good', '1.0').publish()
        buildFile << oneRepository() + lenientResolveTask()

        when:
        bad.pom.expectGet()
        good.allowAll()

        then:
        succeeds 'resolve'
        outputContains("RESOLVED: [good-1.0.jar]")
    }


    def "untrusted TLS certificate stops the search"() {
        given:
        def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
        keyStore.enableSslWithServerCert(server)
        plainServer.start()
        def plainBackupRepo = new MavenHttpRepository(plainServer, '/repo-2', new MavenFileRepository(file('plain-repo-2')))
        plainBackupRepo.module('group', 'a', '1.0').publish().allowAll()

        buildFile << """
            repositories {
                maven { url = "https://localhost:${server.sslPort}/repo" }
                maven { url = "${plainBackupRepo.uri}" }
            }
            ${resolveTask()}
        """

        when:
        executer.withStackTraceChecksDisabled() // Jetty logs handshake failures to the console
        keyStore.configureIncorrectServerCert(executer)

        then:
        fails 'resolve'
        failure.assertHasCause("Could not resolve group:a:1.0.")
        failure.assertThatCause(containsText("java.security.cert.CertPathValidatorException"))
    }

    def "a repository-level failure disables the repository for other modules"() {
        given:
        def bad = mavenHttpRepo.module('group', 'bad', '1.0').publish()
        def good = mavenHttpRepo.module('group', 'good', '1.0').publish()
        buildFile << oneRepository() + lenientResolveTask()

        when:
        bad.pom.expectGetBroken()
        good.allowAll()

        then:
        succeeds 'resolve'
        outputContains("RESOLVED: []")
    }

    def "allowInsecureContinueWhenDisabled lets the search continue past a repository-level failure"() {
        given:
        def first = mavenHttpRepo.module('group', 'a', '1.0').publish()
        def second = backupRepo.module('group', 'a', '1.0').publish()

        buildFile << """
            repositories {
                maven {
                    url = "${mavenHttpRepo.uri}"
                    allowInsecureContinueWhenDisabled = true
                }
                maven { url = "${backupRepo.uri}" }
            }
            ${resolveTask()}
        """

        when:
        first.pom.expectGetBroken()
        second.pom.expectGet()
        second.artifact.expectGet()

        then:
        succeeds 'resolve'
        file('build/libs').assertHasDescendants('a-1.0.jar')
    }


    private String oneRepository() {
        """
            repositories { maven { url = "${mavenHttpRepo.uri}" } }
        """
    }

    private String twoRepositories() {
        """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
                maven { url = "${backupRepo.uri}" }
            }
        """
    }

    private static String resolveTask() {
        """
            configurations { deps }
            dependencies { deps 'group:a:1.0' }
            task resolve(type: Sync) {
                from configurations.deps
                into "\$buildDir/libs"
            }
        """
    }

    private static String lenientResolveTask() {
        """
            configurations { deps }
            dependencies {
                deps 'group:bad:1.0'
                deps 'group:good:1.0'
            }
            task resolve {
                def files = configurations.deps.incoming.artifactView { lenient = true }.files
                doLast { println "RESOLVED: " + files*.name }
            }
        """
    }
}
