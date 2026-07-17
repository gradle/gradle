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
package org.gradle.integtests.resolve.maven

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.AuthScheme

/**
 * Prototype coverage for Maven settings.xml mirror support, behind the
 * {@code org.gradle.internal.mavenMirrors} Gradle property.
 */
class MavenSettingsMirrorIntegrationTest extends AbstractHttpDependencyResolutionTest {
    // Generated with plexus-cipher 2.0: the master password 'gradle-prototype-master' encrypted
    // with the fixed 'settings.security' key, and 'mirror-secret' encrypted with that master
    static final String ENCRYPTED_MASTER = '{+w6zW/gzt3sHKDw2TQ/+vG1479GJ02Z9URESmQqxB26cQ14p5hMV9v+66BoSriyN}'
    static final String ENCRYPTED_PASSWORD = '{0HkYKhjpO2IH/9BoIL1EsU4QYSX9MtFNu23gH81yTeY=}'

    def setup() {
        buildFile << """
            configurations { compile }
            dependencies { compile 'org.test:projectA:1.0' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """
    }

    def "wildcard mirror from maven settings replaces mavenCentral when enabled"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Applying Maven mirror 'test-mirror' for repository 'MavenRepo': ${RepositoryHandler.MAVEN_CENTRAL_URL} -> ${mirrorRepo.uri}")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "mirror is not applied when the feature flag is off"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = originalRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                maven { url = '${originalRepo.uri}' }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        run 'retrieve'

        then:
        outputDoesNotContain("Applying Maven mirror")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "external wildcard mirror does not match local repositories"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = originalRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "external:*", "selective")

        buildFile << """
            repositories {
                maven { url = '${originalRepo.uri}' }
            }
        """

        and: "the repository is served from 127.0.0.1, which external:* excludes"
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputDoesNotContain("Applying Maven mirror")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "mirrorOf central matches the default maven central repository"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "central", "central-mirror")

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Applying Maven mirror 'central-mirror' for repository 'MavenRepo': ${RepositoryHandler.MAVEN_CENTRAL_URL} -> ${mirrorRepo.uri}")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "mirrorOf matches a repository by its gradle name"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "corp-repo", "corp-mirror")

        buildFile << """
            repositories {
                maven {
                    name = 'corp-repo'
                    url = '${originalRepo.uri}'
                }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Applying Maven mirror 'corp-mirror' for repository 'corp-repo': ${originalRepo.uri} -> ${mirrorRepo.uri}")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "blocked mirror fails resolution of the repositories it matches"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        originalRepo.module("org.test", "projectA", "1.0").publish()
        m2.userSettingsFile.text = """
            <settings>
                <mirrors>
                    <mirror>
                        <id>blocker</id>
                        <mirrorOf>*</mirrorOf>
                        <url>http://0.0.0.0/</url>
                        <blocked>true</blocked>
                    </mirror>
                </mirrors>
            </settings>
        """

        buildFile << """
            repositories {
                maven {
                    name = 'corp-repo'
                    url = '${originalRepo.uri}'
                }
            }
        """

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        fails 'retrieve'

        then:
        failure.assertHasCause("Repository 'corp-repo' (${originalRepo.uri}) is blocked by Maven mirror 'blocker' declared in the Maven settings.")
    }

    def "repository credentials are not sent to the mirror"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                maven {
                    url = 'https://original.example.com/repo'
                    credentials {
                        username = 'user'
                        password = 'secret'
                    }
                }
            }
        """

        and: "the mirror would accept the repository credentials, so resolution only fails if they are not sent"
        server.authenticationScheme = AuthScheme.BASIC
        module.pom.allowGetOrHead('user', 'secret')
        module.artifact.allowGetOrHead('user', 'secret')

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        fails 'retrieve'

        then:
        outputContains("Ignoring credentials configured for repository 'maven': its URL is rewritten by Maven mirror 'test-mirror' and the repository credentials do not apply to the mirror.")
        failure.assertHasCause("Could not resolve org.test:projectA:1.0.")
    }

    def "resolves from unauthenticated mirror while ignoring repository credentials"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                maven {
                    url = 'https://original.example.com/repo'
                    credentials {
                        username = 'user'
                        password = 'secret'
                    }
                }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Ignoring credentials configured for repository 'maven': its URL is rewritten by Maven mirror 'test-mirror' and the repository credentials do not apply to the mirror.")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "uses credentials from the maven settings server entry matching the mirror id"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "test-mirror", serverXml("test-mirror", "mirror-user", "mirror-secret"))

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        and:
        server.authenticationScheme = AuthScheme.BASIC
        module.pom.expectGet('mirror-user', 'mirror-secret')
        module.artifact.expectGet('mirror-user', 'mirror-secret')

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Using credentials from the Maven settings server entry 'test-mirror' for Maven mirror 'test-mirror'.")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "decrypts the mirror password with the maven master password"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "test-mirror", serverXml("test-mirror", "mirror-user", ENCRYPTED_PASSWORD))
        m2.userM2Directory.file("settings-security.xml").text = "<settingsSecurity><master>${ENCRYPTED_MASTER}</master></settingsSecurity>"

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        and:
        server.authenticationScheme = AuthScheme.BASIC
        module.pom.expectGet('mirror-user', 'mirror-secret')
        module.artifact.expectGet('mirror-user', 'mirror-secret')

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Using credentials from the Maven settings server entry 'test-mirror' for Maven mirror 'test-mirror'.")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "uses http header from the maven settings server entry for the mirror"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "test-mirror", """
            <servers>
                <server>
                    <id>test-mirror</id>
                    <configuration>
                        <httpHeaders>
                            <property>
                                <name>Private-Token</name>
                                <value>token-123</value>
                            </property>
                        </httpHeaders>
                    </configuration>
                </server>
            </servers>
        """)

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Using HTTP header 'Private-Token' from the Maven settings server entry 'test-mirror' for Maven mirror 'test-mirror'.")
        file('libs').assertHasDescendants('projectA-1.0.jar')

        and: "every request to the mirror carried the header"
        server.allHeaders.every { it.get("Private-Token") == "token-123" }
    }

    def "gradle properties override the maven settings server credentials"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "test-mirror", serverXml("test-mirror", "mirror-user", "outdated-password"))

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        and:
        server.authenticationScheme = AuthScheme.BASIC
        module.pom.expectGet('prop-user', 'prop-secret')
        module.artifact.expectGet('prop-user', 'prop-secret')

        when:
        using m2
        executer.withArguments("-Porg.gradle.internal.mavenMirrors=true", "-Ptest-mirrorUsername=prop-user", "-Ptest-mirrorPassword=prop-secret")
        run 'retrieve'

        then:
        outputContains("Using credentials from Gradle properties 'test-mirrorUsername' and 'test-mirrorPassword' for Maven mirror 'test-mirror'.")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "changing maven settings invalidates the configuration cache when mirrors are enabled"() {
        given:
        def mirrorRepo1 = mavenHttpRepo("mirror1")
        def mirrorRepo2 = mavenHttpRepo("mirror2")
        mirrorRepo1.module("org.test", "projectA", "1.0").publish().allowAll()
        mirrorRepo2.module("org.test", "projectA", "1.0").publish().allowAll()
        writeMirrorSettings(mirrorRepo1.uri.toString())

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        when:
        using m2
        executer.withArguments("--configuration-cache", "-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Applying Maven mirror 'test-mirror' for repository 'MavenRepo': ${RepositoryHandler.MAVEN_CENTRAL_URL} -> ${mirrorRepo1.uri}")

        when:
        writeMirrorSettings(mirrorRepo2.uri.toString())
        using m2
        executer.withArguments("--configuration-cache", "-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        outputContains("Applying Maven mirror 'test-mirror' for repository 'MavenRepo': ${RepositoryHandler.MAVEN_CENTRAL_URL} -> ${mirrorRepo2.uri}")

        and: "the invalidation reason is only logged when not in quiet configuration cache mode"
        GradleContextualExecuter.configCache || output.contains("Maven settings.xml content has changed")
    }

    def "changing maven settings does not invalidate the configuration cache when mirrors are disabled"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        originalRepo.module("org.test", "projectA", "1.0").publish().allowAll()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                maven { url = '${originalRepo.uri}' }
            }
        """

        when:
        using m2
        executer.withArgument("--configuration-cache")
        run 'retrieve'

        then:
        outputDoesNotContain("Applying Maven mirror")

        when:
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "another-mirror")
        using m2
        executer.withArgument("--configuration-cache")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry reused.")
        outputDoesNotContain("Applying Maven mirror")
    }

    private void writeMirrorSettings(String mirrorUrl, String mirrorOf = "*", String id = "test-mirror", String serversXml = "") {
        m2.userSettingsFile.text = """
            <settings>
                <mirrors>
                    <mirror>
                        <id>${id}</id>
                        <mirrorOf>${mirrorOf}</mirrorOf>
                        <url>${mirrorUrl}</url>
                    </mirror>
                </mirrors>
                ${serversXml}
            </settings>
        """
    }

    private static String serverXml(String id, String username, String password) {
        return """
            <servers>
                <server>
                    <id>${id}</id>
                    <username>${username}</username>
                    <password>${password}</password>
                </server>
            </servers>
        """
    }
}
