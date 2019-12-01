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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Unroll

class DependencyVerificationWritingIntegTest extends AbstractDependencyVerificationIntegTest {

    def "can generate an empty verification file"() {
        when:
        succeeds ':help'

        then:
        assertMetadataIsMissing()

        when:
        writeVerificationMetadata()
        succeeds ':help'

        then:
        assertMetadataExists()
        hasNoModules()

        and:
        outputContains("Dependency verification is an incubating feature.")
    }

    @Unroll
    def "warns if trying to generate with an unknown checksum type (#checksums)"() {
        when:
        writeVerificationMetadata(checksums)
        succeeds ':help'

        then:
        outputContains "Invalid checksum type: 'unknown'. You must choose one or more in [md5, sha1, sha256, sha512]"

        where:
        checksums << [
            "unknown",
            "sha1,unknown",
            "md5,unknown,sha1",
            "unknown , sha512"
        ]
    }

    @Unroll
    def "warns if trying to generate only insecure #checksums checksums"() {
        when:
        writeVerificationMetadata(checksums)
        succeeds ':help'

        then:
        outputContains "You chose to generate ${message} checksums but they are all considered insecure. You should consider adding at least one of sha256 or sha512."

        where:
        checksums   | message
        "md5"       | "md5"
        "sha1"      | "sha1"
        "md5, sha1" | "md5 and sha1"

    }

    def "generates verification file for dependencies downloaded during build"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        writeVerificationMetadata()
        run ":compileJava"

        then:
        hasModules(["org:foo"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
        }
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def "generates verification file for dependencies downloaded in previous build (stop in between = #stop)"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        run ":compileJava"
        if (stop) {
            executer.stop()
        }

        then:
        assertMetadataIsMissing()

        when:
        writeVerificationMetadata()
        run ":compileJava"

        then:
        hasModules(["org:foo"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
        }

        where:
        stop << [true, false]
    }

    def "generates checksums for resolvable configurations only"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0")
        uncheckedModule("org", "foo", "1.1")
        uncheckedModule("org", "bar", "1.0")

        buildFile << """
            configurations {
                conf1 {
                    canBeResolved = true
                    canBeConsumed = false
                }
                conf2 {
                    canBeResolved = false
                    canBeConsumed = true
                }
            }

            dependencies {
                conf1 "org:foo:1.0"
                conf2 "org:bar:1.0"
                implementation "org:foo:1.1"
            }
        """

        when:
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
        }
        module("org:foo:1.1") {
            artifact("foo-1.1.jar") {
                declaresChecksums(
                    sha1: "4f61704d48102455b54b20e00bed598b51128184",
                    sha512: "a140b3fa056a88cc228e155a717e4ea5dfbc519f91d9fc9d2a3ab9cdbee118edc834c04dc2abe96d62d2df225fa06083be6fce75a2a7aa0b59e3ae7118a284b1"
                )
            }
        }
    }

    def "can generate checksum for secondary artifacts"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            artifact(type: 'zip')
            artifact(classifier: 'classy')
        }
        buildFile << """
            configurations {
                conf
            }
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:foo:1.0:classy"
                conf "org:foo:1.0@zip"
            }
        """

        when:
        writeVerificationMetadata()
        run ":compileJava"

        then:
        hasModules(["org:foo"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
            artifact("foo-1.0-classy.jar") {
                declaresChecksums(
                    sha1: "57e775f9a7cdbe42752dcb8a18fa1fdedb06a46f",
                    sha512: "77ce252cbb2ffab6f1dc7d1fce84b933106a38f22f12cd21553d6f7be9846f8d53caf0be109f6a78eac0262f10c54651be9b293f805fe175c66f6e609e557e48"
                )
            }
            artifact("foo-1.0.zip") {
                declaresChecksums(
                    sha1: "d94282a5db10b302c7dfc3b685c3746584a06ee3",
                    sha512: "6c9f16dc09b4b5ff9d02ac05418f865552a543633f9e60562b5086841850d0a69775ffa0ea1a618fdc5744840e98feb437560ae64aea097ed3fe385293fb59e8"
                )
            }
        }
    }

    def "generates verification file for dependencies in subprojects"() {
        def mod1 = file("mod1/build.gradle")
        def mod2 = file("mod2/build.gradle")

        given:
        javaLibrary(mod1)
        javaLibrary(mod2)
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")
        mod1 << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """
        mod2 << """
            dependencies {
                implementation "org:bar:1.0"
            }
        """

        when:
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo", "org:bar"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
        }
        module("org:bar") {
            artifact("bar-1.0.jar") {
                declaresChecksums(
                    sha1: "42077067b52edb41c658839ab62a616740417814",
                    sha512: "7bec2082e5447fbbd76285b458f2978194229360cc9aed75a0fc21e2a1b0033137ecf4cbd9883c0a3cfd8b11c176a915500b23d6622aa002c207f48e5043b3b2"
                )
            }
        }
    }

    def "doesn't generate checksums for project and file dependencies"() {
        def mod1 = file("mod1/build.gradle")
        def mod2 = file("mod2/build.gradle")

        given:
        javaLibrary(mod1)
        javaLibrary(mod2)
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")
        mod1 << """
            dependencies {
                implementation "org:foo:1.0"
                api project(":mod2")
            }
        """
        mod2 << """
            dependencies {
                implementation "org:bar:1.0"
                api files("lib/something.jar")
            }
        """

        when:
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo", "org:bar"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
        }
        module("org:bar") {
            artifact("bar-1.0.jar") {
                declaresChecksums(
                    sha1: "42077067b52edb41c658839ab62a616740417814",
                    sha512: "7bec2082e5447fbbd76285b458f2978194229360cc9aed75a0fc21e2a1b0033137ecf4cbd9883c0a3cfd8b11c176a915500b23d6622aa002c207f48e5043b3b2"
                )
            }
        }
    }

    def "doesn't generate checksums for changing dependencies"() {

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
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules([])
    }

    @ToBeFixedForInstantExecution
    def "writes checksums of plugins using plugins block"() {
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
        writeVerificationMetadata()
        succeeds ':help'

        then:
        assertMetadataExists()
        hasModules(["test-plugin:test-plugin.gradle.plugin", "com:myplugin"])
    }

    @ToBeFixedForInstantExecution
    def "writes checksums of plugins using buildscript block"() {
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
        writeVerificationMetadata()
        succeeds ':help'

        then:
        assertMetadataExists()
        hasModules(["com:myplugin"])
    }

    def "shouldn't create local jars when computing checksums of external dependencies"() {
        def m1 = file("mod1/build.gradle")
        def m2 = file("mod2/build.gradle")
        given:
        javaLibrary(m1)
        javaLibrary(m2)
        uncheckedModule("org", "foo")

        m1 << """
            dependencies {
                api project(":mod2")
            }
        """
        m2 << """
            dependencies {
                api "org:foo:1.0"
            }
        """

        when:
        writeVerificationMetadata()
        succeeds ':help'

        then:
        notExecuted(":mod2:compileJava", ":mod2:jar")
        assertMetadataExists()
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
        }
    }

    def "buildSrc dependencies should be included in the generated file"() {
        given:
        file("buildSrc/build.gradle") << """
            repositories {
                maven {
                    url "${mavenHttpRepo.uri}"
                }
            }
            dependencies {
                implementation 'org:foo:1.0'
            }
        """
        javaLibrary()
        buildFile << """
            dependencies {
                implementation 'org:bar:1.0'
            }
        """
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")

        when:
        writeVerificationMetadata()
        succeeds ":help"

        then:
        hasModules(["org:foo", "org:bar"])

    }

    def "buildscript classpath dependencies should be included in the generated file"() {
        given:
        file("build.gradle") << """
            buildscript {
                repositories {
                    maven {
                        url "${mavenHttpRepo.uri}"
                    }
                }
                dependencies {
                    classpath 'org:foo:1.0'
                }
            }
        """
        javaLibrary()
        buildFile << """
            dependencies {
                implementation 'org:bar:1.0'
            }
        """
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")

        when:
        writeVerificationMetadata()
        succeeds ":help"

        then:
        hasModules(["org:foo", "org:bar"])

    }

    def "udpates existing verification file preserving order of entries"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", "md5", "abc")
            addChecksum("org:foo:1.0", "sha1", "1234")
            addChecksum("org:bar:1.0", "sha1", "untouched")
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
        writeVerificationMetadata()
        run ":compileJava"

        then:
        hasModules(["org:foo", "org:bar"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    md5: "abc",
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "e2dfeef03aea02f5a7167c8fd7468ea75ed8e659",
                    sha512: "22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"
                )
            }
        }
        module("org:bar") {
            artifact("bar-1.0.jar") {
                declaresChecksums(
                    sha1: "untouched"
                )
            }
        }

        and:
        assertXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <md5 value="abc"/>
            <sha1 value="1234"/>
         </artifact>
         <artifact name="foo-1.0.jar">
            <sha1 value="16e066e005a935ac60f06216115436ab97c5da02"/>
            <sha512 value="734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"/>
         </artifact>
         <artifact name="foo-1.0.pom">
            <sha1 value="e2dfeef03aea02f5a7167c8fd7468ea75ed8e659"/>
            <sha512 value="22a6b6a05b4d3e49209de90d55b8c67c9cfc238626cbb0c7ad7525ac1dcdc3e4fb406495d512c519745212f16ff3dab4bd47c33b80905ad02ea61d08b8f6ddaa"/>
         </artifact>
      </component>
      <component group="org" name="bar" version="1.0">
         <artifact name="bar-1.0.jar">
            <sha1 value="untouched"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
    }

    def "included build dependencies are used when generating the verification file"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        file("included-build/build.gradle") << """
            plugins {
                id 'java-library'
            }

            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }

            dependencies {
                implementation "org:bar:1.0"
            }
        """

        settingsFile << """
            includeBuild "included-build"
        """

        when:
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo", "org:bar"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
        }
        module("org:bar") {
            artifact("bar-1.0.jar") {
                declaresChecksums(
                    sha1: "42077067b52edb41c658839ab62a616740417814",
                    sha512: "7bec2082e5447fbbd76285b458f2978194229360cc9aed75a0fc21e2a1b0033137ecf4cbd9883c0a3cfd8b11c176a915500b23d6622aa002c207f48e5043b3b2"
                )
            }
        }
    }

    def "writes checksums for parent POMs"() {
        given:
        uncheckedModule("org", "foo", "1.0") {
            parent("org", "parent", "1.0")
        }
        uncheckedModule("org", "parent", "1.0") {
            hasPackaging("pom")

        }
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """
        when:
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo", "org:parent"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
        }
        module("org:parent:1.0") {
            artifact("parent-1.0.pom") {
                declaresChecksums(
                    sha1: "797d515b5e406535205609ca5bbf7ebfb989c832",
                    sha512: "6cb761a43b12a3263b5dee4fa4f960691cc90f44ecc6226ca309b44f86e10b94bf2f602e7bbcd14e10256222c5dfdee28cebeee232fc5824d276b0dd32a00ec4"
                )
            }
        }
    }

    def "writes checksums for Gradle module metadata"() {
        given:
        uncheckedModule("org", "foo", "1.0") {
            withModuleMetadata()
        }
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """
        when:
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
            artifact("foo-1.0.module") {
                declaresChecksums(
                    sha1: "a1a9a2fa2769295b6cef64520662a9a9135e3bb",
                    sha512: "7505ecc6796dd6d0a90a7e422d25c50a7c4b85b21b71ecb43dfca431bb3c3d2f696634c839a315333c96662f92987a9c58719748f6a2017fa5a89913870db60b"
                )
            }
        }
    }

    @NotYetImplemented
    @Unroll
    def "writes checksums for parent POMs downloaded in previous build (stop in between = #stop)"() {
        given:
        uncheckedModule("org", "foo", "1.0") {
            parent("org", "parent", "1.0")
        }
        uncheckedModule("org", "parent", "1.0") {
            hasPackaging("pom")

        }
        javaLibrary()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ':compileJava'

        then:
        assertMetadataIsMissing()
        executedAndNotSkipped(":compileJava")

        when:
        if (stop) {
            executer.stop()
        }
        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo", "org:parent"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "16e066e005a935ac60f06216115436ab97c5da02",
                    sha512: "734fce768f0e1a3aec423cb4804e5cdf343fd317418a5da1adc825256805c5cad9026a3e927ae43ecc12d378ce8f45cc3e16ade9114c9a147fda3958d357a85b"
                )
            }
        }
        module("org:parent:1.0") {
            artifact("parent", "pom", "pom") {
                declaresChecksums(
                    sha1: "797d515b5e406535205609ca5bbf7ebfb989c832",
                    sha512: "6cb761a43b12a3263b5dee4fa4f960691cc90f44ecc6226ca309b44f86e10b94bf2f602e7bbcd14e10256222c5dfdee28cebeee232fc5824d276b0dd32a00ec4"
                )
            }
        }

        where:
        stop << [true, false]
    }
}
