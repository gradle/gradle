/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.verification

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.cache.CachingIntegrationFixture
import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.api.internal.artifacts.verification.DependencyVerificationFixture.getChecksum
import static org.gradle.util.Matchers.containsText

class DependencyVerificationIntegrityCheckIntegTest extends AbstractDependencyVerificationIntegTest implements CachingIntegrationFixture {
    def "doesn't fail if verification metadata matches for #kind"() {
        given:
        javaLibrary()
        def module = uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        createMetadataFile {
            addChecksum(module, kind)
            addChecksum(module, kind, "pom", "pom")
        }

        expect:
        succeeds ":compileJava"

        where:
        kind << ["md5", "sha1", "sha256", "sha512"]
    }

    def "doesn't try to verify checksums for changing dependencies"() {
        createMetadataFile {
            // empty
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0-SNAPSHOT")
        uncheckedModule("org", "bar")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0-SNAPSHOT"
                api("org:bar:1.0") {
                   changing = true
                }
            }
        """

        when:
        run ":compileJava"

        then:
        noExceptionThrown()
    }

    def "fails verifying the file but not resolution itself if verification metadata fails for #kind"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", kind, "invalid")
        }

        given:
        terseConsoleOutput(false)
        javaLibrary()
        def module = uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds "dependencies", "--configuration", "compileClasspath"

        then:
        noExceptionThrown()

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a '$kind' checksum of 'invalid' but was '${getChecksum(module, kind)}'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the checksums.""")

        where:
        kind << ["md5", "sha1", "sha256", "sha512"]
    }

    def "doesn't fail the build but logs errors if lenient mode is used (#param)"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha1', "invalid")
        }

        given:
        terseConsoleOutput(false)
        javaLibrary()
        def foo = uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")
        buildFile << """
            apply plugin: 'java-test-fixtures'
            dependencies {
                implementation "org:foo:1.0"
                testFixturesApi "org:bar:1.0"
            }
        """
        file("src/test/java/HelloTest.java") << "public class HelloTest {}"

        when:
        succeeds([":compileJava", *param] as String[])

        then:
        errorOutput.contains("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1")}'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the checksums.""")

        where:
        param << [["-F", "lenient"], ["--dependency-verification", "lenient"], ["-Dorg.gradle.dependency.verification=lenient"]]
    }

    def "can fully disable verification (#param)"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha1', "invalid")
        }

        given:
        if (param.isEmpty()) {
            disableVerificationViaProjectPropertiesFile()
        }
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds([":compileJava", *param] as String[])

        then:
        !errorOutput.contains("""Dependency verification failed for configuration ':compileClasspath'""")

        where:
        param << [["-F", "off"], ["--dependency-verification", "off"], ["-Dorg.gradle.dependency.verification=off"], []]
    }

    def "can override whatever the gradle.properties file says (#param)"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha1', "invalid")
        }

        given:
        terseConsoleOutput(false)
        disableVerificationViaProjectPropertiesFile()
        javaLibrary()
        def foo = uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds([":compileJava", *param] as String[])

        then:
        errorOutput.contains("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1")}'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the checksums.""")

        where:
        param << [["-F", "lenient"], ["--dependency-verification", "lenient"], ["-Dorg.gradle.dependency.verification=lenient"]]
    }

    private TestFile disableVerificationViaProjectPropertiesFile() {
        file("gradle.properties") << """
        org.gradle.dependency.verification=off
        """
    }

    def "can collect multiple errors in a single dependency graph (terse output=#terse)"() {
        given:
        terseConsoleOutput(terse)
        javaLibrary()
        def foo = uncheckedModule("org", "foo", "1.0") {
            dependsOn("org", "bar", "1.0")
            dependsOn("org", "baz", "1.0")
        }
        def bar = uncheckedModule("org", "bar")
        def baz = uncheckedModule("org", "baz")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        createMetadataFile {
            addChecksum("org:foo:1.0", "sha1", "invalid")
            addChecksum("org:foo:1.0", "sha1", "invalid", "pom", "pom")
            addChecksum("org:bar:1.0", "sha1", "also invalid")
            addChecksum(baz, "sha1")
        }

        when:
        fails ":compileJava"

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
5 artifacts failed verification:
  - bar-1.0.jar (org:bar:1.0) from repository maven
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven
  - bar-1.0.pom (org:bar:1.0) from repository maven
  - baz-1.0.pom (org:baz:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': expected a 'sha1' checksum of 'also invalid' but was '${getChecksum(bar, "sha1")}'
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1")}'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1", "pom")}'
  - On artifact bar-1.0.pom (org:bar:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact baz-1.0.pom (org:baz:1.0) in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
        }
        assertConfigCacheDiscarded()

        where:
        terse << [true, false]
    }

    def "displays repository information (terse output=#terse)"() {
        createMetadataFile {
            noMetadataVerification()
            addChecksum("org:foo:1.0", "sha1", "invalid")
            addChecksum("org:bar:1.0", "sha1", "also invalid")
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        def foo = uncheckedModule("org", "foo", "1.0") {
            dependsOn("org", "bar", "1.0")
        }
        def bar = ivyHttpRepo.module("org", "bar", "1.0")
            .allowAll()
            .publish()
        buildFile << """
            repositories {
                ivy { url = "${ivyHttpRepo.uri}" }
            }

            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - bar-1.0.jar (org:bar:1.0) from repository ivy
  - foo-1.0.jar (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'ivy': expected a 'sha1' checksum of 'also invalid' but was '${getChecksum(bar, "sha1")}'
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1")}'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
        }
        assertConfigCacheDiscarded()

        where:
        terse << [true, false]
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "fails on the first access to an artifact (not at the end of the build) using #firstResolution"() {
        given:
        terseConsoleOutput(false)
        javaLibrary()
        def foo = uncheckedModule("org", "foo", "1.0") {
            withSourceAndJavadoc()
        }
        def bar = uncheckedModule("org", "bar")
        def baz = uncheckedModule("org", "baz")

        createMetadataFile {
            addChecksum("org:foo:1.0", "sha1", "invalid")
            addChecksum(foo, "sha1", "pom", "pom")
            addChecksum("org:bar:1.0", "sha1", "invalid")
            addChecksum(bar, "sha1", "pom", "pom")
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:bar:1.0"
                testImplementation "org:baz:1.0"
            }

            def query = { String module ->
                def ids = configurations.compileClasspath.incoming.resolutionResult.allDependencies
                    .collect { it.selected.id }
                    .findAll { it.module == module }
                println(ids)
                dependencies.createArtifactResolutionQuery()
                    .forComponents(ids)
                    .withArtifacts(JvmLibrary, SourcesArtifact)
                    .execute()
            }

            task resolve {
                doLast {
                    println "First resolution"
                    println $firstResolution
                    println "Second resolution"
                    println configurations.testRuntimeClasspath.files
                }
            }
        """

        when:
        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        fails "resolve"

        then:
        failure.assertHasCause(buildExpectedFailureMessage(failsFooJar, failsBarJar, failsFooSources, foo, bar))

        and:
        outputDoesNotContain("Second resolution")

        where:
        firstResolution                                                                                              | failsFooJar | failsBarJar | failsFooSources
        "configurations.compileClasspath.files"                                                                      | true        | true        | false
        "configurations.compileClasspath.iterator().next()"                                                          | true        | true        | false
        "configurations.compileClasspath.incoming.files.files"                                                       | true        | true        | false
        "configurations.compileClasspath.incoming.files.iterator().next()"                                           | true        | true        | false
        "configurations.compileClasspath.incoming.artifactView {}.files.files"                                       | true        | true        | false
        "configurations.compileClasspath.incoming.artifactView { componentFilter { it.module=='foo' } }.files.files" | true        | false       | false
        "query('foo').resolvedComponents*.getArtifacts(SourcesArtifact)*.file"                                       | false       | false       | true
    }

    private static String buildExpectedFailureMessage(boolean failsFooJar, boolean failsBarJar, boolean failsFooSources, HttpModule foo, HttpModule bar) {
        if (failsFooSources) {
            return """Dependency verification failed for org:foo:1.0:
  - On artifact foo-1.0-sources.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
        }

        String message = """Dependency verification failed for configuration ':compileClasspath':
"""
        if (failsBarJar) {
            message += """  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(bar, "sha1")}'
"""
        }
        if (failsFooJar) {
            message += """  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1")}'
"""
        }
        message += """
This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
        message
    }

    def "fails if any of the checksums (#wrong) declared in the metadata file is wrong"() {
        given:
        terseConsoleOutput(false)
        javaLibrary()
        def foo = uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        createMetadataFile {
            addChecksum("org:foo:1.0", "md5", wrong == "md5" ? "invalid" : getChecksum(foo, "md5"))
            addChecksum("org:foo:1.0", "sha1", wrong == "sha1" ? "invalid" : getChecksum(foo, "sha1"))
        }

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a '$wrong' checksum of 'invalid' but was""")
        assertConfigCacheDiscarded()

        where:
        wrong << ["md5", "sha1"]
    }

    def "can detect a compromised plugin using plugins block"() {
        createMetadataFile {
            addChecksum("test-plugin:test-plugin.gradle.plugin", "sha1", "woot")
            addChecksum("com:myplugin", "sha1", "woot")
        }

        given:
        addPlugin()
        settingsFile.text = """
        pluginManagement {
            repositories {
                maven {
                    url = '$pluginRepo.uri'
                }
            }
        }
        """ + settingsFile.text
        buildFile << """
          plugins {
             id 'test-plugin' version '1.0'
          }
        """

        when:
        fails ':help'

        then:
        failure.assertHasCause("""Dependency verification failed""")
    }

    def "can detect a compromised plugin using buildscript block (terse output=#terse)"() {
        createMetadataFile {
            addChecksum("com:myplugin", "sha1", "woot")
        }

        given:
        terseConsoleOutput(terse)
        addPlugin()
        buildFile << """
          buildscript {
             repositories {
                maven { url = "${pluginRepo.uri}" }
             }
             dependencies {
                classpath 'com:myplugin:1.0'
             }
          }
        """

        when:
        fails ':help'

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration 'classpath'
2 artifacts failed verification:
  - myplugin-1.0.jar (com:myplugin:1.0) from repository maven
  - myplugin-1.0.pom (com:myplugin:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration 'classpath':
  - On artifact myplugin-1.0.jar (com:myplugin:1.0) in repository 'maven': expected a 'sha1' checksum of 'woot' but was"""
        }

        where:
        terse << [true, false]
    }

    def "fails if a dependency doesn't have an associated checksum (terse output=#terse)"() {
        createMetadataFile {
            // nothing in it
        }
        uncheckedModule("org", "foo")

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
        }
        assertConfigCacheDiscarded()

        where:
        terse << [true, false]
    }

    def "ignores project and file dependencies"() {
        createMetadataFile {
            // nothing in it
        }
        given:
        def m1 = file("mod1/build.gradle")
        def m2 = file("mod2/build.gradle")
        javaLibrary(m1)
        javaLibrary(m2)

        m1 << """
            dependencies {
                implementation project(":mod2")
            }
        """

        m2 << """
            dependencies {
                implementation files("lib/other.jar")
            }
        """

        expect:
        succeeds ':mod1:compileJava'

    }

    def "can verify dependencies of buildSrc (terse output=#terse)"() {
        createMetadataFile {
            addChecksum("org:foo", "sha1", "d48c8da6999eb2191744f01691f84675e7ff520b")
        }
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")

        given:
        terseConsoleOutput(terse, "buildSrc")
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """
        def buildSrc = file("buildSrc/build.gradle")
        buildSrc << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }
            dependencies {
               implementation "org:bar:1.0"
            }
        """

        when:
        fails "compileJava"

        then:
        failure.assertHasDescription terse ? """Dependency verification failed for configuration ':buildSrc:buildScriptClasspath'
2 artifacts failed verification:
  - bar-1.0.jar (org:bar:1.0) from repository maven
  - bar-1.0.pom (org:bar:1.0) from repository maven""" : """Dependency verification failed for configuration ':buildSrc:buildScriptClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': checksum is missing from verification metadata."""

        where:
        terse << [true, false]
    }

    def "dependency verification also checks included build dependencies (terse output=#terse)"() {
        createMetadataFile {
        }
        uncheckedModule("org", "bar")

        given:
        terseConsoleOutput(terse, "included")
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:included:1.0"
            }
        """
        def included = file("included/build.gradle")
        included << """
            plugins {
                id 'java-library'
            }
            group = "org"
            version = "1.1-SNAPSHOT"
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }
            dependencies {
               compileOnly "org:bar:1.0"
            }
        """
        file("included/src/main/java/org/included/Included.java") << """
            package org.included;
            public class Included {}
        """

        when:
        fails "compileJava", "--include-build", "included"

        then:
        failure.assertHasCause terse ? """Dependency verification failed for configuration ':included:compileClasspath'
2 artifacts failed verification:
  - bar-1.0.jar (org:bar:1.0) from repository maven
  - bar-1.0.pom (org:bar:1.0) from repository maven""" : """Dependency verification failed for configuration ':included:compileClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': checksum is missing from verification metadata."""

        assertConfigCacheDiscarded()

        where:
        terse << [true, false]
    }

    @Issue("https://github.com/gradle/gradle/issues/4934")
    def "can detect a tampered file in the local cache (terse output=#terse)"() {
        def foo = uncheckedModule("org", "foo")
        def fooChecksum = getChecksum(foo, "sha1")

        createMetadataFile {
            addChecksum(foo, "sha1")
            addChecksum(foo, "sha1", "pom", "pom")
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ':compileJava'

        then:
        noExceptionThrown()
        executer.stop()

        when:
        def group = new File(CacheLayout.FILE_STORE.getPath(metadataCacheDir), "org")
        def module = new File(group, "foo")
        def version = new File(module, "1.0")
        def originHash = new File(version, fooChecksum)
        def artifactFile = new File(originHash, "foo-1.0.jar")
        artifactFile.text = "tampered"

        fails ':compileJava'

        then:
        failure.assertHasCause terse ? """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the checksums.""" : """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of '${fooChecksum}' but was '93d6c93d9a76d27ec3462e7b57de5df1eb45bc7b'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""

        where:
        terse << [true, false]
    }

    /**
     * This test case is NOT about security but detecting tampered metadata files.
     * In practice, if you update a metadata file in the local cache, it would be unnoticed
     * because Gradle always uses the binary version instead. So this is about warning the
     * user that someone did something wrong by thinking that updating a file in our local
     * cache should change something in terms of resolution.
     *
     * Security is not an issue: if someone manages to tamper the local cache, then
     * it means they have access to the local FS so all bets are off.
     */
    @Issue("https://github.com/gradle/gradle/issues/4934")
    def "can detect a tampered metadata file in the local cache (stop in between = #stop)"() {
        def foo = uncheckedModule("org", "foo")
        def fooPomChecksum = getChecksum(foo, "sha1", "pom")
        createMetadataFile {
            addChecksum(foo, "sha1")
            addChecksum(foo, "sha1", "pom", "pom")
        }

        given:
        terseConsoleOutput(false)
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ':compileJava'

        then:
        noExceptionThrown()
        if (stop) {
            executer.stop()
        }

        when:
        def group = new File(CacheLayout.FILE_STORE.getPath(metadataCacheDir), "org")
        def module = new File(group, "foo")
        def version = new File(module, "1.0")
        def originHash = new File(version, fooPomChecksum)
        def artifactFile = new File(originHash, "foo-1.0.pom")
        artifactFile.text = "tampered"

        fails ':compileJava'

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of '${fooPomChecksum}' but was '93d6c93d9a76d27ec3462e7b57de5df1eb45bc7b'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""

        where:
        stop << [true, false]
    }

    def "can skip verification of metadata"() {
        given:
        javaLibrary()
        def foo = uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """
        createMetadataFile {
            noMetadataVerification()
            addChecksum(foo, "sha1")
        }

        expect:
        succeeds ":compileJava"
    }

    def "can skip verification of parent POM"() {
        given:
        javaLibrary()
        uncheckedModule("org", "parent", "1.0")
        def foo = uncheckedModule("org", "foo", "1.0") {
            parent("org", "parent", "1.0")
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        createMetadataFile {
            noMetadataVerification()
            addChecksum(foo, "sha1")
        }

        expect:
        succeeds ":compileJava"
    }

    def "can trust some artifacts"() {
        createMetadataFile {
            addChecksum("org:baz:1.0", "sha1", "caf4fe86ac24e52f35d4001f5e02261e6a9f3785", "pom", "pom")
            trust("org", "foo", "1.0")
            trust("org", "bar")
            trust("org", "baz", "1.0", "baz-1.0.jar")
            trust("org2", "ta.*", null, null, true)
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0")
        uncheckedModule("org", "bar", "1.0")
        uncheckedModule("org", "baz", "1.0")
        uncheckedModule("org2", "tada", "1.1")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:bar:1.0"
                implementation "org:baz:1.0"
                implementation "org2:tada:1.1"
            }
        """

        expect:
        succeeds ":compileJava"
    }

    def "doesn't fail if verification metadata matches for #kind using alternate checksum"() {
        given:
        javaLibrary()
        def foo = uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        createMetadataFile {
            addChecksum("org:foo:1.0", kind, "primary-jar")
            addChecksum(foo, kind, "jar")
            addChecksum("org:foo:1.0", kind, "primary-pom", "pom", "pom")
            addChecksum(foo, kind, "pom", "pom")
        }

        expect:
        succeeds ":compileJava"

        where:
        kind << ["md5", "sha1", "sha256", "sha512"]
    }

    def "reasonable error message when the verification file can't be parsed"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        file("gradle/verification-metadata.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>true</verify-signatures>
   </configuration>
    <trusted-keys>
         <trusted-key id="4db1a49729b053caf015cee9a6adfc93ef34893e" group="org.hamcrest"/>
      </trusted-keys>
</verification-metadata>
"""
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        errorOutput.contains("> Could not resolve all dependencies for configuration ':compileClasspath'.")
        errorOutput.contains("   > Dependency verification cannot be performed")
        errorOutput.contains("      > Unable to read dependency verification metadata from")
        errorOutput.contains("         > Invalid dependency verification metadata file: <trusted-keys> must be found under the <configuration> tag")
        failure.assertThatCause(containsText("Dependency verification cannot be performed"))
    }

    def "can disable verification for specific configurations (terse output=#terse)"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha1', "invalid")
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        def foo = uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
            configurations.compileClasspath.resolutionStrategy.disableDependencyVerification()

            tasks.register("resolveRuntime") {
                def runtimeClasspath = configurations.runtimeClasspath
                doLast {
                    println runtimeClasspath.files
                }
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputContains "Dependency verification has been disabled."

        when:
        fails ":resolveRuntime"

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':runtimeClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':runtimeClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1")}'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata."""
        }

        where:
        terse << [true, false]
    }

    def "fails validation when first configuration has verification disabled"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha256', "invalid")
            addChecksum("org:foo:1.0", 'sha256', "f331cce36f6ce9ea387a2c8719fabaf67dc5a5862227ebaa13368ff84eb69481", "pom", "pom")
        }
        javaLibrary()

        given:
        uncheckedModule("org", "foo")
        buildFile << """
            configurations {
                unverified {
                    resolutionStrategy.disableDependencyVerification()
                }
            }

            dependencies {
                unverified "org:foo:1.0"
                implementation "org:foo:1.0"
            }

            tasks.register("printConfigurations") {
                FileCollection unverified = configurations.unverified
                FileCollection classpath = configurations.compileClasspath
                doLast {
                    println unverified.files
                    println classpath.files
                }
            }
        """

        when:
        fails ":printConfigurations"

        then:
        failure.assertThatCause(containsText("""Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the checksums."""))
        assertConfigCacheDiscarded()
    }

    def "can disable verification of a detached configuration (terse output=#terse)"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha1', "invalid")
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        def foo = uncheckedModule("org", "foo")
        buildFile << """
            tasks.register("resolve") {
                def conf = configurations.detachedConfiguration(dependencies.create("org:foo:1.0"))
                if (project.hasProperty("disableVerification")) {
                    conf.resolutionStrategy.disableDependencyVerification()
                }
                doLast {
                    println conf.files
                }
            }
        """

        when:
        fails ":resolve"

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':detachedConfiguration1'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':detachedConfiguration1':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '${getChecksum(foo, "sha1")}'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata."""
        }

        when:
        succeeds ":resolve", "-PdisableVerification=true"

        then:
        outputContains "Dependency verification has been disabled."

        where:
        terse << [true, false]
    }

    def "handles artifacts cleaned by the cache cleanup"() {
        def mod = mavenHttpRepo.module('org', 'foo', '1.0')
            .publish()

        createMetadataFile {
            addChecksum(mod, "sha1")
            addChecksum(mod, "sha1", "pom", "pom")
        }

        def oldSha1Sum = getChecksum(mod, "sha1")
        def oldPomSha1Sum = getChecksum(mod, "sha1", "pom")

        given:
        terseConsoleOutput(false)
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        mod.pom.expectGet()
        mod.artifact.expectGet()

        then:
        succeeds ':compileJava'

        when:
        markForArtifactCacheCleanup()

        then:
        succeeds ':help'

        when:
        mod.pom.expectGet()
        mod.artifact.expectGet()

        then:
        succeeds ':compileJava'

        when:
        markForArtifactCacheCleanup()
        mod.publishWithChangedContent()
        succeeds ':help'

        then:
        mod.pom.expectGet()
        mod.artifact.expectGet()
        fails ':compileJava'

        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of '$oldSha1Sum' but was '${getChecksum(mod, "sha1")}'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of '$oldPomSha1Sum' but was '${getChecksum(mod, "sha1", "pom")}'
"""
    }

    def "reasonable error message for dependencies of init scripts which are missing from verification file"() {
        file("init.gradle") << """
            initscript {
                repositories {
                    maven { url = "${mavenHttpRepo.uri}" }
                }
                dependencies {
                    classpath 'org:foo:1.0'
                }
            }
        """

        def mod = mavenHttpRepo.module('org', 'foo', '1.0')
            .publish()

        createMetadataFile {
        }

        buildFile << """
            tasks.register("noop") {
            }
        """

        when:
        mod.pom.expectGet()
        mod.artifact.expectGet()
        executer.withArguments("-I", "init.gradle")
        fails 'noop'

        then:
        failure.assertHasDescription """Dependency verification failed for configuration 'classpath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    @Issue("https://github.com/gradle/gradle/issues/18498")
    def "fails validation for local repository with cached metadata rule"() {
        def repoDir = testDirectory.createDir("repo")
        buildFile << """
            plugins {
                id 'java'
            }
            @CacheableRule
            abstract class SamplesVariantRule implements ComponentMetadataRule {

                @Inject
                abstract ObjectFactory getObjectFactory()

                void execute(ComponentMetadataContext ctx) {
                    def variant = "samplessources"
                    def id = ctx.details.id
                    org.gradle.api.attributes.Category category = objectFactory.named(org.gradle.api.attributes.Category, org.gradle.api.attributes.Category.DOCUMENTATION)
                    DocsType docsType = objectFactory.named(DocsType, variant)
                    ctx.details.addVariant(variant) { VariantMetadata vm ->
                        vm.attributes{ AttributeContainer ac ->
                            ac.attribute(org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE, category)
                            ac.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, docsType)
                        }
                        vm.withFiles {
                            it.addFile("\${id.name}-\${id.version}-\${variant}.jar")
                        }
                    }

                }
            }
            repositories {
                maven { url = "${repoDir.toURI()}" }
            }
            dependencies {
                components.all(SamplesVariantRule)
                implementation('org:monitor:1.0')
            }
            tasks.register('resolveCompileClasspath') {
                configurations.compileClasspath.resolve()
            }
        """
        mavenLocal(repoDir).module('org', 'monitor', '1.0').publish()
        createMetadataFile {
            // Just so we enable dependency verification
            addChecksum("org:dummy:1.0", "sha256", "some-value", "pom", "pom")
        }

        when:
        fails "resolveCompileClasspath"

        then:
        failure.assertThatCause(containsText("""
2 artifacts failed verification:
  - monitor-1.0.jar (org:monitor:1.0) from repository maven
  - monitor-1.0.pom (org:monitor:1.0) from repository maven"""))

        when:
        executer.requireIsolatedDaemons()
        fails "resolveCompileClasspath"

        then:
        failure.assertThatCause(containsText("""
2 artifacts failed verification:
  - monitor-1.0.jar (org:monitor:1.0) from repository maven
  - monitor-1.0.pom (org:monitor:1.0) from repository maven"""))
    }

    def "fails validation when input validation has failed"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha256', "invalid")
            addChecksum("org:foo:1.0", 'sha256', "f331cce36f6ce9ea387a2c8719fabaf67dc5a5862227ebaa13368ff84eb69481", "pom", "pom")
        }
        javaLibrary()

        given:
        uncheckedModule("org", "foo")
        buildFile << """
            class PrintConfigurations extends DefaultTask {
                @InputFiles
                FileCollection classpath;

                @TaskAction
                void execute() {
                    println "classpath: " + classpath.files
                }
            }

            dependencies {
                implementation "org:foo:1.0"
            }

            tasks.register("printConfigurations", PrintConfigurations) {
                classpath = configurations.compileClasspath
            }
        """

        when:
        fails ":printConfigurations"

        then:
        failure.assertThatCause(containsText("""Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the checksums."""))
        assertConfigCacheDiscarded()
    }
}
