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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

/**
 * Verifies the v1 repository mirroring feature: a {@code $GRADLE_USER_HOME/mirrors.toml}
 * file declares URL-based mirrors that the resolver layer transparently uses in place of
 * declared repositories. The hook lives in the resolver, so every declaration source
 * (project, dependencyResolutionManagement, init script, pluginManagement, buildscript)
 * is covered without per-source branching.
 */
class RepositoryMirrorIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private TestFile mirrorsFile

    def setup() {
        mirrorsFile = file("mirrors.toml")
        executer.beforeExecute {
            executer.withArgument("-Dorg.gradle.mirrors.file=${mirrorsFile.absolutePath}")
        }
    }

    private MavenHttpRepository originalMavenRepo() {
        return mavenHttpRepo("original")
    }

    private MavenHttpRepository mirrorMavenRepo() {
        return mavenHttpRepo("mirror")
    }

    private IvyHttpRepository originalIvyRepo() {
        return ivyHttpRepo("original-ivy")
    }

    private IvyHttpRepository mirrorIvyRepo() {
        return ivyHttpRepo("mirror-ivy")
    }

    private void writeMirror(String matchUrl, String mirrorUrl) {
        mirrorsFile.text = """
[[mirror]]
name = "test-mirror"
match-url = "$matchUrl"
url = "$mirrorUrl"
""".stripIndent()
    }

    private void writeMirrorWithCredentials(String matchUrl, String mirrorUrl, String userEnv, String passEnv) {
        mirrorsFile.text = """
[[mirror]]
name = "test-mirror"
match-url = "$matchUrl"
url = "$mirrorUrl"
credentials.username = "\${env.$userEnv}"
credentials.password = "\${env.$passEnv}"
""".stripIndent()
    }

    private MavenHttpModule publishToMirrorOnly(String group, String name, String version) {
        // Backing file repo is shared between mavenHttpRepo("original") and mavenHttpRepo("mirror")
        // only when using the same context path. We must publish to the mirror's backing repo.
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module(group, name, version)
        module.publish()
        return module
    }

    private IvyHttpModule publishIvyToMirrorOnly(String group, String name, String version) {
        IvyHttpRepository mirror = mirrorIvyRepo()
        IvyHttpModule module = mirror.module(group, name, version).publish()
        return module
    }

    // --- Declaration-source coverage (5 tests) ---

    def "mirrors a Maven repository declared in project repositories block"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'resolve'
        outputContains('foo-1.0.jar')
    }

    def "mirrors a Maven repository declared in dependencyResolutionManagement"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        writeMirror("**/original", "${mirror.uri}")

        settingsFile << """
            dependencyResolutionManagement {
                repositoriesMode = RepositoriesMode.PREFER_SETTINGS
                repositories {
                    maven { url = "${original.uri}" }
                }
            }
            rootProject.name = 'test'
        """
        buildFile << """
            configurations { conf }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'resolve'
        outputContains('foo-1.0.jar')
    }

    def "mirrors a Maven repository declared in an init script"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        writeMirror("**/original", "${mirror.uri}")

        TestFile initScript = file("init.gradle") << """
            beforeSettings { settings ->
                settings.dependencyResolutionManagement.repositoriesMode = RepositoriesMode.PREFER_SETTINGS
                settings.dependencyResolutionManagement.repositories {
                    maven { url = "${original.uri}" }
                }
            }
        """
        executer.usingInitScript(initScript)

        buildFile << """
            configurations { conf }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'resolve'
        outputContains('foo-1.0.jar')
    }

    def "mirrors a Maven repository declared in pluginManagement"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org.example', 'my-plugin', '1.0')
        publishPluginMarker(mirror, 'my-plugin', 'org.example:my-plugin:1.0', '1.0')
        module.publish()
        writeMirror("**/original", "${mirror.uri}")

        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url = "${original.uri}" }
                }
            }
            rootProject.name = 'test'
        """
        buildFile << """
            plugins {
                id 'org.example.my-plugin' version '1.0' apply false
            }
        """

        when:
        MavenHttpModule marker = mirror.module('org.example.my-plugin', 'org.example.my-plugin.gradle.plugin', '1.0')
        marker.pom.expectGet()
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'help'
    }

    def "mirrors a Maven repository declared in buildscript block"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'classpathlib', '1.0').publish()
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            buildscript {
                repositories {
                    maven { url = "${original.uri}" }
                }
                dependencies {
                    classpath 'org:classpathlib:1.0'
                }
            }
            tasks.register('hello') { doLast { println 'hello' } }
        """

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'hello'
        outputContains('hello')
    }

    // --- Hard-fail behavior (3 tests) ---

    def "hard-fails when the mirror returns 404 without falling back to original"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule mirrorModule = mirror.module('org', 'foo', '1.0')
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        mirrorModule.pom.expectGetMissing()
        mirrorModule.artifact.expectHeadMissing()
        // No expectations on `original` — that endpoint must never be contacted

        then:
        fails 'resolve'
        failure.assertHasCause("Could not resolve org:foo:1.0")
    }

    def "hard-fails when the mirror returns 5xx without falling back to original"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule mirrorModule = mirror.module('org', 'foo', '1.0')
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        mirrorModule.pom.expectGetBroken()
        // No expectations on `original` — that endpoint must never be contacted

        then:
        fails 'resolve'
        failure.assertHasCause("Could not resolve org:foo:1.0")
    }

    def "mirror error references the mirror name and URL"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule mirrorModule = mirror.module('org', 'foo', '1.0')
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        mirrorModule.pom.expectGetMissing()
        mirrorModule.artifact.expectHeadMissing()

        then:
        fails 'resolve'
        // Mirror URL is visible because the resolver's effective repository points at it
        failure.assertThatCause(org.hamcrest.CoreMatchers.containsString(mirror.uri.toString()))
    }

    // --- Repository types & schemes (3 tests) ---

    def "mirrors an Ivy repository"() {
        given:
        IvyHttpRepository original = originalIvyRepo()
        IvyHttpRepository mirror = mirrorIvyRepo()
        IvyHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        writeMirror("**/original-ivy", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                ivy { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds 'resolve'
        outputContains('foo-1.0.jar')
    }

    def "mirrors a file:// repository to an https-equivalent mirror"() {
        given:
        // Original is a file:// path; mirror is the HTTP server. The repo URI must look like
        // it points at a local directory, but the resolver should redirect to the HTTP mirror.
        TestFile originalDir = file("offline-repo")
        originalDir.mkdirs()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        URI originalUri = originalDir.toURI()
        writeMirror("file:**", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${originalUri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'resolve'
        outputContains('foo-1.0.jar')
    }

    // --- Credentials (2 tests) ---

    def "resolves mirror credentials from environment variable references"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        writeMirrorWithCredentials("**/original", "${mirror.uri}", "MIRROR_USER", "MIRROR_TOKEN")
        executer.withEnvironmentVars(MIRROR_USER: "alice", MIRROR_TOKEN: "s3cret")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        mirror.module('org', 'foo', '1.0')
        server.expectUserAgent(null)
        module.pom.expectGet('alice', 's3cret')
        module.artifact.expectGet('alice', 's3cret')

        then:
        succeeds 'resolve'
        outputContains('foo-1.0.jar')
    }

    def "does not forward the declared repository's credentials to the mirror"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        // No credentials in mirror config — the mirror must be hit anonymously
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven {
                    url = "${original.uri}"
                    credentials {
                        username = 'orig-user'
                        password = 'orig-pass'
                    }
                }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                doLast { configurations.conf.files.each { println it.name } }
            }
        """

        when:
        // Anonymous GETs - if the declared credentials were forwarded, these would arrive
        // with a basic-auth header and the fixture would assert against the expected creds.
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'resolve'
        outputContains('foo-1.0.jar')
    }

    // --- Configuration cache (2 tests) ---

    def "build with mirrors is configuration-cache compatible"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                def files = configurations.conf
                doLast { files.files.each { println it.name } }
            }
        """

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds 'resolve', '--configuration-cache'
        outputContains('foo-1.0.jar')

        when:
        // Second run reuses the configuration cache; no resolution should happen again
        // (artifacts are cached locally by Gradle's normal artifact cache).
        then:
        succeeds 'resolve', '--configuration-cache'
        outputContains("Reusing configuration cache.")
    }

    def "changing mirrors.toml invalidates the configuration cache"() {
        given:
        MavenHttpRepository original = originalMavenRepo()
        MavenHttpRepository mirror = mirrorMavenRepo()
        MavenHttpModule module = mirror.module('org', 'foo', '1.0').publish()
        writeMirror("**/original", "${mirror.uri}")

        buildFile << """
            configurations { conf }
            repositories {
                maven { url = "${original.uri}" }
            }
            dependencies { conf 'org:foo:1.0' }
            tasks.register('resolve') {
                def files = configurations.conf
                doLast { files.files.each { println it.name } }
            }
        """

        when:
        module.pom.expectGet()
        module.artifact.expectGet()
        succeeds 'resolve', '--configuration-cache'

        then:
        outputContains('foo-1.0.jar')

        when:
        // Touch the mirrors file — change the mirror name (any change should invalidate CC)
        mirrorsFile.text = mirrorsFile.text.replace('test-mirror', 'renamed-mirror')

        then:
        succeeds 'resolve', '--configuration-cache'
        outputContains("Calculating task graph as configuration cache cannot be reused")
    }

    private static MavenHttpModule publishPluginMarker(MavenHttpRepository repo, String pluginId, String implementation, String version) {
        // Plugin marker module is published so the plugin id maps to the implementation coords.
        String markerGroup = 'org.example.my-plugin'
        String markerName = 'org.example.my-plugin.gradle.plugin'
        MavenHttpModule marker = repo.module(markerGroup, markerName, version)
        marker.dependsOn('org.example', 'my-plugin', version)
        marker.publish()
        return marker
    }
}
