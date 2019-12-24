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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.cache.CachingIntegrationFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.util.Matchers.containsText

class DependencyVerificationIntegrityCheckIntegTest extends AbstractDependencyVerificationIntegTest implements CachingIntegrationFixture {
    @Unroll
    def "doesn't fail if verification metadata matches for #kind"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", kind, jar)
            addChecksum("org:foo:1.0", kind, pom, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputContains("Dependency verification is an incubating feature.")

        where:
        kind     | jar                                                                                                                                | pom
        "md5"    | "ea8b622874eaa501476e0ebbe0c562ed"                                                                                                 | "9ecdc5a5aaf0fb15d0e1c5d1760d477c"
        "sha1"   | "16e066e005a935ac60f06216115436ab97c5da02"                                                                                         | "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02"
        "sha256" | "20ae575ede776e5e06ee6b168652d11ee23069e92de110fdec13fbeaa5cf3bbc"                                                                 | "f331cce36f6ce9ea387a2c8719fabaf67dc5a5862227ebaa13368ff84eb69481"
        "sha512" | "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b" | "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
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

    @Unroll
    @ToBeFixedForInstantExecution
    def "fails verifying the file but not resolution itself if verification metadata fails for #kind"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", kind, "invalid")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds "dependencies", "--configuration", "compileClasspath"

        then:
        outputContains("Dependency verification is an incubating feature.")

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a '$kind' checksum of 'invalid' but was '$value'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the checksums.""")

        where:
        kind     | value
        "md5"    | "ea8b622874eaa501476e0ebbe0c562ed"
        "sha1"   | "16e066e005a935ac60f06216115436ab97c5da02"
        "sha256" | "20ae575ede776e5e06ee6b168652d11ee23069e92de110fdec13fbeaa5cf3bbc"
        "sha512" | "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
    }

    @Unroll
    def "doesn't fail the build but logs errors if lenient mode is used (#param)"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha1', "invalid")
        }

        given:
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
        errorOutput.contains("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '16e066e005a935ac60f06216115436ab97c5da02'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the checksums.""")

        where:
        param << [["-F", "lenient"], ["--dependency-verification", "lenient"], ["-Dorg.gradle.dependency.verification=lenient"]]
    }

    @Unroll
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

    @Unroll
    def "can override whatever the gradle.properties file says (#param)"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", 'sha1', "invalid")
        }

        given:
        disableVerificationViaProjectPropertiesFile()
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
        errorOutput.contains("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '16e066e005a935ac60f06216115436ab97c5da02'
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


    def "can collect multiple errors in a single dependency graph"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", "sha1", "invalid")
            addChecksum("org:foo:1.0", "sha1", "invalid", "pom", "pom")
            addChecksum("org:bar:1.0", "sha1", "also invalid")
            addChecksum("org:baz:1.0", "sha1", "c554a4a45e3ed3da494befb446fb2923b8bcecef")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            dependsOn("org", "bar", "1.0")
            dependsOn("org", "baz", "1.0")
        }
        uncheckedModule("org", "bar")
        uncheckedModule("org", "baz")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': expected a 'sha1' checksum of 'also invalid' but was '42077067b52edb41c658839ab62a616740417814'
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '16e066e005a935ac60f06216115436ab97c5da02'
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '6db079f8f24050d849647e029da573999776b635'
  - On artifact bar-1.0.pom (org:bar:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact baz-1.0.pom (org:baz:1.0) in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the checksums.""")
    }

    def "displays repository information"() {
        createMetadataFile {
            noMetadataVerification()
            addChecksum("org:foo:1.0", "sha1", "invalid")
            addChecksum("org:bar:1.0", "sha1", "also invalid")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            dependsOn("org", "bar", "1.0")
        }
        ivyHttpRepo.module("org","bar", "1.0")
            .allowAll()
            .publish()
        buildFile << """
            repositories {
                ivy { url "${ivyHttpRepo.uri}" }
            }

            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'ivy': expected a 'sha1' checksum of 'also invalid' but was '42077067b52edb41c658839ab62a616740417814'
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '16e066e005a935ac60f06216115436ab97c5da02'

This can indicate that a dependency has been compromised. Please carefully verify the checksums.""")
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def "fails on the first access to an artifact (not at the end of the build) using #firstResolution"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", "sha1", "invalid")
            addChecksum("org:foo:1.0", "sha1", "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02", "pom", "pom")
            addChecksum("org:bar:1.0", "sha1", "invalid")
            addChecksum("org:bar:1.0", "sha1", "302ecc047ad29b30546a6419fbd5bd58755ff2a0", "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSourceAndJavadoc()
        }
        uncheckedModule("org", "bar")
        uncheckedModule("org", "baz")
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
                inputs.files(configurations.compileClasspath)
                inputs.files(configurations.testRuntimeClasspath)
                doLast {
                    println "First resolution"
                    println $firstResolution
                    println "Second resolution"
                    println configurations.testRuntimeClasspath.files
                }
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause(buildExpectedFailureMessage(failsFooJar, failsBarJar, failsFooSources))

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

    private static String buildExpectedFailureMessage(boolean failsFooJar, boolean failsBarJar, boolean failsFooSources) {
        if (failsFooSources) {
            return """Dependency verification failed for org:foo:1.0:
  - On artifact foo-1.0-sources.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

If the dependency is legit, follow the instructions at ${docsUrl}"""
        }

        String message = """Dependency verification failed for configuration ':compileClasspath':
"""
        if (failsBarJar) {
            message += """  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '42077067b52edb41c658839ab62a616740417814'
"""
        }
        if (failsFooJar) {
            message += """  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of 'invalid' but was '16e066e005a935ac60f06216115436ab97c5da02'
"""
        }
        message += """
This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
        message
    }

    @Unroll
    def "fails if any of the checksums (#wrong) declared in the metadata file is wrong"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", "md5", md5)
            addChecksum("org:foo:1.0", "sha1", sha1)
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a '$wrong' checksum of 'invalid' but was""")

        where:
        wrong  | md5                                | sha1
        "md5"  | "invalid"                          | "16e066e005a935ac60f06216115436ab97c5da02"
        "sha1" | "ea8b622874eaa501476e0ebbe0c562ed" | "invalid"
    }

    @ToBeFixedForInstantExecution
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
                    url '$pluginRepo.uri'
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

    @ToBeFixedForInstantExecution
    def "can detect a compromised plugin using buildscript block"() {
        createMetadataFile {
            addChecksum("com:myplugin", "sha1", "woot")
        }

        given:
        addPlugin()
        buildFile << """
          buildscript {
             repositories {
                maven { url "${pluginRepo.uri}" }
             }
             dependencies {
                classpath 'com:myplugin:1.0'
             }
          }
        """

        when:
        fails ':help'

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':classpath':
  - On artifact myplugin-1.0.jar (com:myplugin:1.0) in repository 'maven': expected a 'sha1' checksum of 'woot' but was""")
    }

    def "fails if a dependency doesn't have an associated checksum"() {
        createMetadataFile {
            // nothing in it
        }
        uncheckedModule("org", "foo")

        given:
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.

If the dependency is legit, follow the instructions at ${docsUrl}""")
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

        when:
        succeeds ':mod1:compileJava'

        then:
        outputContains("Dependency verification is an incubating feature.")
    }

    def "can verify dependencies of buildSrc"() {
        createMetadataFile {
            addChecksum("org:foo", "sha1", "16e066e005a935ac60f06216115436ab97c5da02")
        }
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")

        given:
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """
        def buildSrc = file("buildSrc/build.gradle")
        buildSrc << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            dependencies {
               implementation "org:bar:1.0"
            }
        """

        when:
        fails "compileJava"

        then:
        failure.assertHasDescription """Dependency verification failed for configuration ':buildSrc:runtimeClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': checksum is missing from verification metadata."""
    }

    @ToBeFixedForInstantExecution
    def "dependency verification also checks included build dependencies"() {
        createMetadataFile {
            addChecksum("org:foo", "sha1", "16e066e005a935ac60f06216115436ab97c5da02")
        }
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")

        given:
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
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
                maven { url "${mavenHttpRepo.uri}" }
            }
            dependencies {
               implementation "org:bar:1.0"
            }
        """
        file("included/src/main/java/org/included/Included.java") << """
            package org.included;
            public class Included {}
        """

        when:
        fails "compileJava", "--include-build", "included"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':included:compileClasspath':
  - On artifact bar-1.0.jar (org:bar:1.0) in repository 'maven': checksum is missing from verification metadata."""
    }

    @Issue("https://github.com/gradle/gradle/issues/4934")
    @ToBeFixedForInstantExecution
    def "can detect a tampered file in the local cache"() {
        createMetadataFile {
            addChecksum("org:foo", "sha1", "16e066e005a935ac60f06216115436ab97c5da02")
            addChecksum("org:foo", "sha1", "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02", "pom", "pom")
        }
        uncheckedModule("org", "foo")

        given:
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
        def originHash = new File(version, "16e066e005a935ac60f06216115436ab97c5da02")
        def artifactFile = new File(originHash, "foo-1.0.jar")
        artifactFile.text = "tampered"

        fails ':compileJava'

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of '16e066e005a935ac60f06216115436ab97c5da02' but was '93d6c93d9a76d27ec3462e7b57de5df1eb45bc7b'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
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
    @ToBeFixedForInstantExecution
    @Unroll
    def "can detect a tampered metadata file in the local cache (stop in between = #stop)"() {
        createMetadataFile {
            addChecksum("org:foo", "sha1", "16e066e005a935ac60f06216115436ab97c5da02")
            addChecksum("org:foo", "sha1", "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02", "pom", "pom")
        }
        uncheckedModule("org", "foo")

        given:
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
        def originHash = new File(version, "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02")
        def artifactFile = new File(originHash, "foo-1.0.pom")
        artifactFile.text = "tampered"

        fails ':compileJava'

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': expected a 'sha1' checksum of '85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02' but was '93d6c93d9a76d27ec3462e7b57de5df1eb45bc7b'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""

        where:
        stop << [true, false]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def "deleting local artifacts fails verification (stop in between = #stop)"() {
        createMetadataFile {
            addChecksum("org:foo", "sha1", "16e066e005a935ac60f06216115436ab97c5da02")
            addChecksum("org:foo", "sha1", "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02", "pom", "pom")
        }
        uncheckedModule("org", "foo")

        given:
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
        def originHash = new File(version, "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02")
        def artifactFile = new File(originHash, "foo-1.0.pom")
        artifactFile.delete()

        fails ':compileJava'

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': artifact file has been deleted from local cache so verification cannot be performed"""

        where:
        stop << [true, false]
    }

    def "can skip verification of metadata"() {
        createMetadataFile {
            noMetadataVerification()
            addChecksum("org:foo:1.0", "sha1", "16e066e005a935ac60f06216115436ab97c5da02")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputContains("Dependency verification is an incubating feature.")

    }

    def "can skip verification of parent POM"() {
        createMetadataFile {
            noMetadataVerification()
            addChecksum("org:foo:1.0", "sha1", "16e066e005a935ac60f06216115436ab97c5da02")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "parent", "1.0")
        uncheckedModule("org", "foo", "1.0") {
            parent("org", "parent", "1.0")
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputContains("Dependency verification is an incubating feature.")
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

        when:
        succeeds ":compileJava"

        then:
        outputContains("Dependency verification is an incubating feature.")
    }

    @Unroll
    def "doesn't fail if verification metadata matches for #kind using alternate checksum"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", kind, "primary-jar")
            addChecksum("org:foo:1.0", kind, jar)
            addChecksum("org:foo:1.0", kind, "primary-pom", "pom", "pom")
            addChecksum("org:foo:1.0", kind, pom, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputContains("Dependency verification is an incubating feature.")

        where:
        kind     | jar                                                                                                                                | pom
        "md5"    | "ea8b622874eaa501476e0ebbe0c562ed"                                                                                                 | "9ecdc5a5aaf0fb15d0e1c5d1760d477c"
        "sha1"   | "16e066e005a935ac60f06216115436ab97c5da02"                                                                                         | "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02"
        "sha256" | "20ae575ede776e5e06ee6b168652d11ee23069e92de110fdec13fbeaa5cf3bbc"                                                                 | "f331cce36f6ce9ea387a2c8719fabaf67dc5a5862227ebaa13368ff84eb69481"
        "sha512" | "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b" | "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
    }

    def "reasonable error message when the verification file can't be parsed"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        file("gradle/verification-metadata.xml") << "j'adore les fruits au sirop"
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertThatCause(containsText("verification-metadata.xml"))
        failure.assertThatCause(containsText("Dependency verification cannot be performed because the configuration couldn't be read:"))
    }

    private static String getDocsUrl() {
        new DocumentationRegistry().getDocumentationFor("dependency_verification", "sec:troubleshooting-verification")
    }
}
