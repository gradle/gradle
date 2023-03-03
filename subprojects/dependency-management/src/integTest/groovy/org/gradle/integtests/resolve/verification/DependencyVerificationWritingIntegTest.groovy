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
import org.gradle.integtests.fixtures.cache.CachingIntegrationFixture
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository
import spock.lang.Issue

class DependencyVerificationWritingIntegTest extends AbstractDependencyVerificationIntegTest implements CachingIntegrationFixture {

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
    }

    def "warns if trying to generate with an unknown checksum type (#checksums)"() {
        when:
        writeVerificationMetadata(checksums)
        succeeds ':help'

        then:
        output.contains "Invalid checksum type: 'unknown'. You must choose one or more in [md5, sha1, sha256, sha512, pgp]"

        where:
        checksums << [
            "unknown,md5",
            "sha1,unknown",
            "md5,unknown,sha1",
            "unknown , sha512"
        ]
    }

    def "warns if trying to generate only insecure #checksums checksums"() {
        when:
        writeVerificationMetadata(checksums)
        succeeds ':help'

        then:
        output.contains "You chose to generate ${message} checksums but they are all considered insecure. You should consider adding at least one of sha256 or sha512 or pgp."

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
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
        }
    }

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
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
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

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        hasModules(["org:foo"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
        }
        module("org:foo:1.1") {
            artifact("foo-1.1.jar") {
                declaresChecksums(
                    sha1: "265d8de18be8c7af862c0b8df3ca1a00aeda5a86",
                    sha512: "bef78d6836dfbb3b667433c59df2324ac1847092664bfdd085779a0a7c40899051215b5a6ab44e9542bb04092253a0974750c97952b7a65b16e1fff0fbb124e8"
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
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
            artifact("foo-1.0-classy.jar") {
                declaresChecksums(
                    sha1: "841c539a412cfb7c4470824f4d94049bbbee3a34",
                    sha512: "6bdd477f0ff4b6036b4f52780ce621c65ff6f6eea7c7dbf5eb4f8bbd923581826f6c232d690798b2c4e586d616782e1b4e8aeabefd4b7faaa149216286a095bd"
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

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        hasModules(["org:foo", "org:bar"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
        }
        module("org:bar") {
            artifact("bar-1.0.jar") {
                declaresChecksums(
                    sha1: "14ec73769c3116a6a741a5ced0717f50689180c9",
                    sha512: "b4965a27cd27bfd28ee2da2671ead68af4eedddf7959d356353590c29a9126815634ff4c37834b9508b4cf96ef136ca54c94d653ed3c8084f8a1784e80a2c715"
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
        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo", "org:bar"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
        }
        module("org:bar") {
            artifact("bar-1.0.jar") {
                declaresChecksums(
                    sha1: "14ec73769c3116a6a741a5ced0717f50689180c9",
                    sha512: "b4965a27cd27bfd28ee2da2671ead68af4eedddf7959d356353590c29a9126815634ff4c37834b9508b4cf96ef136ca54c94d653ed3c8084f8a1784e80a2c715"
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

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        hasModules([])
    }

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

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        succeeds ':help'

        then:
        notExecuted(":mod2:compileJava", ":mod2:jar")
        assertMetadataExists()
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
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
        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

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
        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        writeVerificationMetadata()
        succeeds ":help"

        then:
        hasModules(["org:foo", "org:bar"])

    }

    def "udpates existing verification file sorting entries"() {
        createMetadataFile {
            addChecksum("org:foo:1.0", "md5", "abc")
            addChecksum("org:foo:1.0", "sha1", "1234")
            addChecksum("org:bar:1.0", "sha1", "untouched")
            trust("dummy", "artifact")
            trust("other", "artifact", "with", "file.jar", true)
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
                    sha1: ["1234", "d48c8da6999eb2191744f01691f84675e7ff520b"],
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
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
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <trusted-artifacts>
         <trust group="dummy" name="artifact"/>
         <trust group="other" name="artifact" version="with" file="file.jar" regex="true"/>
      </trusted-artifacts>
   </configuration>
   <components>
      <component group="org" name="bar" version="1.0">
         <artifact name="bar-1.0.jar">
            <sha1 value="untouched"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <md5 value="abc"/>
            <sha1 value="1234" origin="Generated by Gradle">
               <also-trust value="d48c8da6999eb2191744f01691f84675e7ff520b"/>
            </sha1>
            <sha512 value="328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="foo-1.0.pom">
            <sha1 value="85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02" origin="Generated by Gradle"/>
            <sha512 value="3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe" origin="Generated by Gradle"/>
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
        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo", "org:bar"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
        }
        module("org:bar") {
            artifact("bar-1.0.jar") {
                declaresChecksums(
                    sha1: "14ec73769c3116a6a741a5ced0717f50689180c9",
                    sha512: "b4965a27cd27bfd28ee2da2671ead68af4eedddf7959d356353590c29a9126815634ff4c37834b9508b4cf96ef136ca54c94d653ed3c8084f8a1784e80a2c715"
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

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        hasModules(["org:foo", "org:parent"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
        }
        module("org:parent:1.0") {
            artifact("parent-1.0.pom") {
                declaresChecksums(
                    sha1: "dcf91b67fc14846f8234ef8e9cac922721cabf80",
                    sha512: "01d797bd76f86414d7d7184522663bc7a28faaf19310caf5458a156dded879a914bd5c151ccc3553a9f65c4e58a85e8ec917692d517f770aaf7debacbf0fcbaf"
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
        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        writeVerificationMetadata()
        run ":help"

        then:
        hasModules(["org:foo"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.module") {
                declaresChecksums(
                    sha1: "5cb00c1d3c96a6f47cf3f4129a0db4fcd371b1ed",
                    sha512: "fa852fb8aab53474f4e498026557806de823797398ac86402636801eebbb13bd2c53b64fbefe5c3c038a64fca3c7b70d76865c5d644e9e9c80a50b4076afe997"
                )
            }
        }
    }

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

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        hasModules(["org:foo", "org:parent"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
        }
        module("org:parent:1.0") {
            artifact("parent-1.0.pom") {
                declaresChecksums(
                    sha1: "dcf91b67fc14846f8234ef8e9cac922721cabf80",
                    sha512: "01d797bd76f86414d7d7184522663bc7a28faaf19310caf5458a156dded879a914bd5c151ccc3553a9f65c4e58a85e8ec917692d517f770aaf7debacbf0fcbaf"
                )
            }
        }

        where:
        stop << [true, false]
    }

    def "doesn't write artifact metadata when metadata verification is disabled (gmm=#gmm)"() {
        createMetadataFile {
            noMetadataVerification()
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            if (gmm) {
                withModuleMetadata()
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        writeVerificationMetadata("sha1")
        succeeds ":compileJava"

        then:
        hasModules(["org:foo"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                )
            }
        }

        and:
        assertXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>false</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="d48c8da6999eb2191744f01691f84675e7ff520b" origin="Generated by Gradle"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""

        where:
        gmm << [false, true]
    }

    def "doesn't write checksums of parent POMs if metadata verification is disabled"() {
        createMetadataFile {
            noMetadataVerification()
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
        writeVerificationMetadata("sha1")
        succeeds ":compileJava"

        then:
        hasModules(["org:foo"])
        module("org:foo:1.0") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                )
            }
        }

        and:
        assertXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>false</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="d48c8da6999eb2191744f01691f84675e7ff520b" origin="Generated by Gradle"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""

    }


    def "updating a file doesn't generate duplicates"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar", "1.0") {
            artifact(classifier: 'classy')
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:bar:1.0:classy"
            }
        """

        when:
        writeVerificationMetadata()
        run ":compileJava"

        then:
        hasModules(["org:foo", "org:bar"])
        assertXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="bar" version="1.0">
         <artifact name="bar-1.0-classy.jar">
            <sha1 value="6a0119874f39b8301a2f60a4a89dbd0824cebcd2" origin="Generated by Gradle"/>
            <sha512 value="3eea438b2702a9c7077a4e80ebaedd7d58c1d4d92d304347eef0e9062fbe6ea1cb7d282aefbc6bfddb9303e5bd7f74ad54135dfc80ca68c934e40fef6350165d" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="bar-1.0.pom">
            <sha1 value="302ecc047ad29b30546a6419fbd5bd58755ff2a0" origin="Generated by Gradle"/>
            <sha512 value="d0ccda043f53a984382ed7345e21335cacbafc95e5ce536f1aa844c4a6c6bfc837d44bfae0dc28a756d8771c9594e9b3f86f6dcdecae62bfda596e0c21f7ed1d" origin="Generated by Gradle"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="d48c8da6999eb2191744f01691f84675e7ff520b" origin="Generated by Gradle"/>
            <sha512 value="328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="foo-1.0.pom">
            <sha1 value="85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02" origin="Generated by Gradle"/>
            <sha512 value="3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe" origin="Generated by Gradle"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""

        when:
        writeVerificationMetadata()
        succeeds ":compileJava"

        then:
        assertXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="bar" version="1.0">
         <artifact name="bar-1.0-classy.jar">
            <sha1 value="6a0119874f39b8301a2f60a4a89dbd0824cebcd2" origin="Generated by Gradle"/>
            <sha512 value="3eea438b2702a9c7077a4e80ebaedd7d58c1d4d92d304347eef0e9062fbe6ea1cb7d282aefbc6bfddb9303e5bd7f74ad54135dfc80ca68c934e40fef6350165d" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="bar-1.0.pom">
            <sha1 value="302ecc047ad29b30546a6419fbd5bd58755ff2a0" origin="Generated by Gradle"/>
            <sha512 value="d0ccda043f53a984382ed7345e21335cacbafc95e5ce536f1aa844c4a6c6bfc837d44bfae0dc28a756d8771c9594e9b3f86f6dcdecae62bfda596e0c21f7ed1d" origin="Generated by Gradle"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="d48c8da6999eb2191744f01691f84675e7ff520b" origin="Generated by Gradle"/>
            <sha512 value="328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="foo-1.0.pom">
            <sha1 value="85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02" origin="Generated by Gradle"/>
            <sha512 value="3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe" origin="Generated by Gradle"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
    }

    // This test verifies that the writer "sees" all checksums in a single
    // build, even if it means that something is fishy (an artifact used in the build
    // can have different checksums if it comes from different repositories, but the user
    // needs to be aware of it)
    def "can write alternate checksums in a single build"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        def alternateRepo = new MavenFileRepository(
            testDirectory.createDir("alternate-repo")
        )
        MavenFileModule otherFile = alternateRepo.module("org", "foo", "1.0")
            .publish()
        otherFile.artifactFile.bytes = [0, 0, 0, 0]

        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """
        settingsFile << "include 'other'"
        file("other/build.gradle") << """
            repositories {
                  maven { url "${alternateRepo.uri}" }
            }
            configurations {
                other
            }
            dependencies {
                other "org:foo:1.0"
            }
            tasks.register("resolveAnother") {
                doLast { println configurations.other.files }
            }
        """

        when:
        writeVerificationMetadata()
        succeeds ":compileJava", "other:resolveAnother"

        then:
        hasModules(["org:foo"])
        module("org:foo") {
            artifact("foo-1.0.jar") {
                declaresChecksums(
                    sha1: ["d48c8da6999eb2191744f01691f84675e7ff520b", "9069ca78e7450a285173431b3e52c5c25299e473"],
                    sha512: ["328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba", "ec2d57691d9b2d40182ac565032054b7d784ba96b18bcb5be0bb4e70e3fb041eff582c8af66ee50256539f2181d7f9e53627c0189da7e75a4d5ef10ea93b20b3"]
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
        }
    }

    def "skips writing checksums for artifacts declared as trusted"() {
        given:
        createMetadataFile {
            trust("org", "bar")
        }
        javaLibrary()
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:bar:1.0"
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
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
        }
    }

    def "skips writing checksums for artifacts declared as trusted unless they were already in the file"() {
        given:
        createMetadataFile {
            addChecksum("org:bar:1.1", "md5", "abc")
            trust("org", "bar")
        }
        javaLibrary()
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:bar:1.0"
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
                    sha1: "d48c8da6999eb2191744f01691f84675e7ff520b",
                    sha512: "328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba"
                )
            }
            artifact("foo-1.0.pom") {
                declaresChecksums(
                    sha1: "85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02",
                    sha512: "3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe"
                )
            }
        }
        hasNoModule("org:bar:1.0")
        module("org:bar:1.1") {
            artifact("bar-1.1.jar") {
                declaresChecksums(
                    md5: "abc"
                )
            }
        }
    }

    def "can use --dry-run to write a different file for comparison"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar", "1.0")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        writeVerificationMetadata()
        run ":compileJava"

        then:
        assertXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="d48c8da6999eb2191744f01691f84675e7ff520b" origin="Generated by Gradle"/>
            <sha512 value="328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="foo-1.0.pom">
            <sha1 value="85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02" origin="Generated by Gradle"/>
            <sha512 value="3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe" origin="Generated by Gradle"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""

        when:
        buildFile << """
            dependencies {
                implementation "org:bar:1.0"
            }
        """
        writeVerificationMetadata()
        succeeds ":compileJava", "--dry-run"

        then:
        assertXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="d48c8da6999eb2191744f01691f84675e7ff520b" origin="Generated by Gradle"/>
            <sha512 value="328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="foo-1.0.pom">
            <sha1 value="85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02" origin="Generated by Gradle"/>
            <sha512 value="3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe" origin="Generated by Gradle"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""

        and:
        assertDryRunXmlContents """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="bar" version="1.0">
         <artifact name="bar-1.0.jar">
            <sha1 value="14ec73769c3116a6a741a5ced0717f50689180c9" origin="Generated by Gradle"/>
            <sha512 value="b4965a27cd27bfd28ee2da2671ead68af4eedddf7959d356353590c29a9126815634ff4c37834b9508b4cf96ef136ca54c94d653ed3c8084f8a1784e80a2c715" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="bar-1.0.pom">
            <sha1 value="302ecc047ad29b30546a6419fbd5bd58755ff2a0" origin="Generated by Gradle"/>
            <sha512 value="d0ccda043f53a984382ed7345e21335cacbafc95e5ce536f1aa844c4a6c6bfc837d44bfae0dc28a756d8771c9594e9b3f86f6dcdecae62bfda596e0c21f7ed1d" origin="Generated by Gradle"/>
         </artifact>
      </component>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <sha1 value="d48c8da6999eb2191744f01691f84675e7ff520b" origin="Generated by Gradle"/>
            <sha512 value="328114e6f92f888c200ea6889d9ba0c940ca260e81fcaeb238d583d7fab96fab451288afee1153dc9bf93caa33200583151f5d9aa500bbebc13a3dae92218bba" origin="Generated by Gradle"/>
         </artifact>
         <artifact name="foo-1.0.pom">
            <sha1 value="85a7b8a2eb6bb1c4cdbbfe5e6c8dc3757de22c02" origin="Generated by Gradle"/>
            <sha512 value="3d890ff72a2d6fcb2a921715143e6489d8f650a572c33070b7f290082a07bfc4af0b64763bcf505e1c07388bc21b7d5707e50a3952188dc604814e09387fbbfe" origin="Generated by Gradle"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""
    }

    def "doesn't write verification metadata for skipped configurations"() {
        javaLibrary()
        uncheckedModule("org", "foo")
        uncheckedModule("org", "bar")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                runtimeOnly "org:bar:1.0"
            }
        """

        when:
        writeVerificationMetadata()

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        hasModules(["org:foo", "org:bar"])

        when:
        deleteMetadataFile()
        buildFile << """
            configurations.all {
               resolutionStrategy.disableDependencyVerification()
               if (name == 'compileClasspath') {
                  resolutionStrategy.enableDependencyVerification()
               }
            }
        """
        writeVerificationMetadata()

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        outputContains "Dependency verification has been disabled for configuration runtimeClasspath"
        hasModules(["org:foo"])
    }

    @Issue("https://github.com/gradle/gradle/issues/12260")
    def "doesn't fail writing verification file if a #artifact file is missing from local store"() {
        javaLibrary()
        uncheckedModule("org", "foo")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        run ":compileJava"

        then:
        noExceptionThrown()

        when:
        def group = new File(CacheLayout.FILE_STORE.getPath(metadataCacheDir), "org")
        def module = new File(group, "foo")
        def version = new File(module, "1.0")
        version.eachFileRecurse {
            if (it.name.endsWith(".${artifact}")) {
                it.delete()
            }
        }

        writeVerificationMetadata()

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help", "--offline"

        then:
        hasModules(artifact == 'pom' ? [] : ["org:foo"])

        where:
        artifact << ['jar', 'pom']
    }

    def "keeps trust reasons"() {
        given:
        def expectedXmlContents = """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
      <trusted-artifacts>
         <trust group="dummy" name="artifact"/>
         <trust group="other" name="artifact" reason="sample trust reason"/>
         <trust group="fourth" name="artifact" version="with" file="file.jar" regex="true" reason="another sample reason"/>
         <trust group="third" name="artifact" version="with" file="file.jar" regex="true"/>
      </trusted-artifacts>
   </configuration>
   <components/>
</verification-metadata>
"""

        when:
        createMetadataFile {
            trust("dummy", "artifact")
            trust("other", "artifact", null, null, false, "sample trust reason")
            trust("third", "artifact", "with", "file.jar", true)
            trust("fourth", "artifact", "with", "file.jar", true, "another sample reason")
        }

        then:
        assertXmlContents expectedXmlContents

        and:
        javaLibrary()
        buildFile << """
        """

        when:
        writeVerificationMetadata()

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        assertXmlContents expectedXmlContents
    }

    def "keeps checksum reasons"() {
        given:
        def expectedXmlContents = """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata>
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>false</verify-signatures>
   </configuration>
   <components>
      <component group="org" name="foo" version="1.0">
         <artifact name="foo-1.0.jar">
            <md5 value="abc" reason="test checksum"/>
         </artifact>
      </component>
   </components>
</verification-metadata>
"""

        when:
        createMetadataFile {
            addChecksum("org:foo:1.0", "md5", "abc", "jar", "jar", null, "test checksum")
        }

        then:
        assertXmlContents expectedXmlContents

        and:
        javaLibrary()
        buildFile << """
        """

        when:
        writeVerificationMetadata()

        //TODO: remove this once dependency verification stops triggering dependency resolution at execution time
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")

        run ":help"

        then:
        assertXmlContents expectedXmlContents
    }
}
