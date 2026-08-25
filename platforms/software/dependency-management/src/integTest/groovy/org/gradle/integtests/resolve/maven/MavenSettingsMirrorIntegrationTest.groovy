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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpServer

/**
 * Prototype coverage for Maven settings.xml mirror support, behind the
 * {@code org.gradle.mirror.maven.settings} Gradle property.
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "warns that the feature is incubating, once per build"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                mavenCentral()
                maven { url = '${mavenHttpRepo("second").uri}' }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then: "two repositories consult the mirrors, but the warning is not repeated"
        output.count("Reusing Maven mirror settings is an incubating feature.") == 1
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "mavenLocal is never mirrored"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        // No HTTP expectations are declared for the mirror, so contacting it fails the build
        mirrorRepo.module("org.test", "projectA", "1.0").publish()
        m2.mavenRepo().module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                mavenLocal()
            }
        """

        when:
        using m2
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then: "the wildcard mirror does not apply to the file based local repository"
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        fails 'retrieve'

        then:
        failure.assertHasCause("Repository 'corp-repo' (${originalRepo.uri}) is blocked by Maven mirror 'blocker' declared in the Maven settings.")
    }

    def "maven default http blocker does not block a gradle declared repository"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def module = originalRepo.module("org.test", "projectA", "1.0").publish()
        // Verbatim from the conf/settings.xml Maven has shipped since 3.8.1, except for mirrorOf:
        // the fixture server is on 127.0.0.1, which external:http:* deliberately excludes, so it
        // cannot match end to end. MirrorOfMatcherTest covers external:http:* itself.
        m2.globalSettingsFile.text = """
            <settings>
                <mirrors>
                    <mirror>
                        <id>maven-default-http-blocker</id>
                        <mirrorOf>*</mirrorOf>
                        <name>Pseudo repository to mirror external repositories initially using HTTP.</name>
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
                    allowInsecureProtocol = true
                }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then: "Gradle already refuses plain http unless allowInsecureProtocol is set, so Maven's installation default adds nothing"
        outputDoesNotContain("is blocked by Maven mirror")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "an insecure mirror of a secure repository fails without allowInsecureProtocol"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        mirrorRepo.module("org.test", "projectA", "1.0").publish().allowAll()
        // GUtil.isSecureUrl exempts http://127.0.0.1 so the HTTP fixtures work, so the mirror has
        // to be addressed as localhost for the insecure protocol check to see it as insecure
        writeMirrorSettings(mirrorRepo.uri.toString().replace("127.0.0.1", "localhost"))

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        when: "the repository is https, so the check passes on the declared url"
        using m2
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        fails 'retrieve'

        then: "the url actually contacted is what has to be secure, not the one that was declared"
        failure.assertHasCause("Using insecure protocols with repositories, without explicit opt-in, is unsupported.")
    }

    def "an insecure mirror is allowed when the repository opts in with allowInsecureProtocol"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString().replace("127.0.0.1", "localhost"))

        buildFile << """
            repositories {
                maven {
                    name = 'corp-repo'
                    url = 'https://repo.example.com/maven2'
                    allowInsecureProtocol = true
                }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "publishing authenticates with the repository credentials, not the mirror's"() {
        given: "a wildcard mirror with its own credentials; mirrors apply to resolution, never to publishing"
        def publishRepo = mavenHttpRepo("publish")
        def module = publishRepo.module("org.test", "publishA", "1.0")
        writeMirrorSettings(mavenHttpRepo("mirror").uri.toString(), "*", "test-mirror",
            serverXml("test-mirror", "mirror-user", "mirror-secret"))

        settingsFile << "rootProject.name = 'publishA'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            group = 'org.test'
            version = '1.0'
            publishing {
                publications { mavenJava(MavenPublication) { from components.java } }
                repositories {
                    maven {
                        url = '${publishRepo.uri}'
                        credentials {
                            username = 'repo-user'
                            password = 'repo-secret'
                        }
                    }
                }
            }
        """

        and:
        def credentials = new HttpServer.PasswordCredentials('repo-user', 'repo-secret')
        server.authenticationScheme = AuthScheme.BASIC
        module.artifact.expectPublish(true, credentials)
        module.pom.expectPublish(true, credentials)
        module.moduleMetadata.expectPublish(true, credentials)
        module.rootMetaData.expectGetMissing(credentials)
        module.rootMetaData.expectPublish(true, credentials)

        when:
        using m2
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'publish'

        then: "the mirror's credentials would have been rejected by the server with a 401"
        module.artifactFile.assertIsCopyOf(file("build/libs/publishA-1.0.jar"))
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        fails 'retrieve'

        then:
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "decrypts the mirror password with the maven master password"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "test-mirror", serverXml("test-mirror", "mirror-user", ENCRYPTED_PASSWORD))
        m2.userM2Directory.file("settings-security.xml").text = securitySettings()

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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
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
        executer.withArgument("-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        and: "every request to the mirror carried the header"
        server.allHeaders.every { it.get("Private-Token") == "token-123" }
    }

    def "changing maven settings invalidates the configuration cache when mirrors are enabled"() {
        given:
        def mirrorRepo1 = mavenHttpRepo("mirror1")
        def mirrorRepo2 = mavenHttpRepo("mirror2")
        def module1 = mirrorRepo1.module("org.test", "projectA", "1.0").publish()
        def module2 = mirrorRepo2.module("org.test", "projectA", "1.0").publish()
        // Only the mirror in force is served, so reaching for the other one fails the build
        module1.allowAll()
        writeMirrorSettings(mirrorRepo1.uri.toString())

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        when:
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        when:
        writeMirrorSettings(mirrorRepo2.uri.toString())
        server.resetExpectations()
        module2.allowAll()
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry stored.")

        and: "the invalidation reason names the settings file that changed"
        GradleContextualExecuter.configCache || output.contains("settings.xml' has changed")
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
        file('libs').assertHasDescendants('projectA-1.0.jar')

        when:
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "another-mirror")
        using m2
        executer.withArgument("--configuration-cache")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry reused.")
    }

    def "configuration cache is reused when the maven settings are unchanged and mirrors are enabled"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        mirrorRepo.module("org.test", "projectA", "1.0").publish().allowAll()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        when:
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry stored.")

        when: "nothing changes"
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then: "the settings checksum is stable, so the entry is not invalidated"
        postBuildOutputContains("Configuration cache entry reused.")
    }

    def "changing the maven settings security file invalidates the configuration cache when mirrors are enabled"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        mirrorRepo.module("org.test", "projectA", "1.0").publish().allowAll()
        writeMirrorSettings(mirrorRepo.uri.toString())
        def securityFile = m2.userM2Directory.file("settings-security.xml")
        securityFile.text = securitySettings()

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        when:
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry stored.")

        when: "settings-security.xml changes but settings.xml does not"
        securityFile.text = securitySettings("<!-- rotated -->")
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry stored.")

        and: "the invalidation reason names the settings file that changed"
        GradleContextualExecuter.configCache || output.contains("settings-security.xml' has changed")
    }

    private static String securitySettings(String extra = "") {
        return "<settingsSecurity>${extra}<master>${ENCRYPTED_MASTER}</master></settingsSecurity>"
    }

    def "changing maven settings re-mirrors every project, not only the first one configured"() {
        given: "two projects that each declare their own mirrored repository"
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo1 = mavenHttpRepo("mirror1")
        def mirrorRepo2 = mavenHttpRepo("mirror2")
        def module1 = mirrorRepo1.module("org.test", "projectA", "1.0").publish()
        def module2 = mirrorRepo2.module("org.test", "projectA", "1.0").publish()
        // Only the mirror in force is served, so a project that keeps the old one fails the build
        module1.allowAll()
        writeMirrorSettings(mirrorRepo1.uri.toString())

        createDirs("a", "b")
        settingsFile << "include 'a', 'b'"
        file("a/build.gradle").text = mirroredProject("repo-a", originalRepo.uri.toString())
        file("b/build.gradle").text = mirroredProject("repo-b", originalRepo.uri.toString())

        when:
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run ':a:retrieve', ':b:retrieve'

        then:
        postBuildOutputContains("Configuration cache entry stored.")

        when: "the settings name a different mirror"
        writeMirrorSettings(mirrorRepo2.uri.toString())
        server.resetExpectations()
        module2.allowAll()
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run ':a:retrieve', ':b:retrieve'

        then: "the whole entry is invalidated, so neither project keeps the old mirror"
        postBuildOutputContains("Configuration cache entry stored.")
        file('a/libs').assertHasDescendants('projectA-1.0.jar')
        file('b/libs').assertHasDescendants('projectA-1.0.jar')
    }

    private static String mirroredProject(String repositoryName, String repositoryUrl) {
        return """
            configurations { compile }
            dependencies { compile 'org.test:projectA:1.0' }
            repositories {
                maven {
                    name = '${repositoryName}'
                    url = '${repositoryUrl}'
                }
            }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """
    }

    def "turning the feature on does not reuse a configuration cache entry stored with it off"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        def originalModule = originalRepo.module("org.test", "projectA", "1.0").publish()
        def mirrorModule = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        // Only the repository that should be used is served, so reusing a stale entry fails the build
        originalModule.allowAll()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                maven { url = '${originalRepo.uri}' }
            }
        """

        when: "the feature is off"
        using m2
        executer.withArgument("--configuration-cache")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        when: "the feature is turned on and only the mirror is served"
        server.resetExpectations()
        mirrorModule.allowAll()
        using m2
        executer.withArguments("--configuration-cache", "-Dorg.gradle.mirror.maven.settings=true")
        run 'retrieve'

        then: "the stale entry is not reused, so the mirror actually applies"
        file('libs').assertHasDescendants('projectA-1.0.jar')
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
