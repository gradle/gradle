/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.capabilities

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class CapabilitiesConflictResolutionIssuesIntegrationTest extends AbstractIntegrationSpec {

    def resolve = new ResolveTestFixture(buildFile, "runtimeClasspath")

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/14770")
    def "capabilities resolution shouldn't put graph in inconsistent state"() {
        file("shared/build.gradle") << """
            plugins {
                id("java-library")
            }

            sourceSets {
                one {}
                onePref {}
                two {}
                twoPref {}
            }

            java {
                registerFeature('one') {
                    usingSourceSet(sourceSets.one)
                    capability('o', 'n', 'e')
                    capability('g', 'one', 'v')
                }
                registerFeature('onePreferred') {
                    usingSourceSet(sourceSets.onePref)
                    capability('o', 'n', 'e')
                    capability('g', 'one-preferred', 'v')
                }

                registerFeature('two') {
                    usingSourceSet(sourceSets.two)
                    capability('t', 'w', 'o')
                    capability('g', 'two', 'v')
                }
                registerFeature('twoPreferred') {
                    usingSourceSet(sourceSets.twoPref)
                    capability('t', 'w', 'o')
                    capability('g', 'two-preferred', 'v')
                }
            }

            dependencies {
                twoImplementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one:v')
                    }
                }
                twoPrefImplementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one-preferred:v')
                    }
                }
            }
        """
        file("p1/build.gradle") << """
            apply plugin: 'java'

            dependencies {
                implementation project(':p2')
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one-preferred:v')
                    }
                }
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:two-preferred:v')
                    }
                }
            }

            configurations.compileClasspath {
                resolutionStrategy.capabilitiesResolution.all { details ->
                    def selection =
                        details.candidates.find { it.variantName.endsWith('PrefApiElements') }
                    println("Selecting \$selection from \${details.candidates}")
                    details.select(selection)
                }
            }

            configurations.runtimeClasspath {
                resolutionStrategy.capabilitiesResolution.all { details ->
                    def selection =
                        details.candidates.find { it.variantName.endsWith('PrefRuntimeElements') }
                    println("Selecting \$selection from \${details.candidates}")
                    details.select(selection)
                }
            }
        """
        file("p2/build.gradle") << """
            apply plugin: 'java'

            dependencies {
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one:v')
                    }
                }
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:two:v')
                    }
                }
            }
        """
        settingsFile << """
            include 'shared'
            include 'p1'
            include 'p2'
        """

        when:
        resolve.prepare()
        run ":p1:checkDeps"

        then:
        resolve.expectGraph {
            root(":p1", "test:p1:") {
                project(":p2", "test:p2:") {
                    configuration 'runtimeElements'
                    project(":shared", "test:shared:") {
                        artifact(classifier: 'one-preferred')
                    }
                    project(":shared", "test:shared:") {
                        artifact(classifier: 'two-preferred')
                    }
                }
                project(":shared", "test:shared:") {
                    variant('onePrefRuntimeElements', [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': "${JavaVersion.current().majorVersion}",
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-runtime'])
                    byConflictResolution()
                    project(":shared", "test:shared:") {

                    }
                }
                project(":shared", "test:shared:") {
                    variant('twoPrefRuntimeElements', [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': "${JavaVersion.current().majorVersion}",
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-runtime'])
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/14220#issuecomment-1423804572")
    def "pending dependencies are transferred to new module after capability conflict"() {

        mavenRepo.module("org.hibernate", "hibernate-core", "5.4.18.Final")
            .dependsOn("org.dom4j", "dom4j", "2.1.3", null, null, null, [[group: "*", module: "*"]])
            .publish()
        mavenRepo.module("jaxen", "jaxen", "1.1.1")
            .dependsOn(mavenRepo.module("dom4j", "dom4j", "1.6.1").publish())
            .publish()
        mavenRepo.module("org.dom4j", "dom4j", "2.1.3").publish()
            .dependsOn(mavenRepo.module("jaxen", "jaxen", "1.1.6").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation 'org.hibernate:hibernate-core:5.4.18.Final'
                implementation 'jaxen:jaxen:1.1.1'
            }
        """

        capability("org.dom4j", "dom4j") {
            forModule("dom4j:dom4j")
            selectHighest()
        }

        when:
        resolve.prepare()
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.hibernate:hibernate-core:5.4.18.Final") {
                    module("org.dom4j:dom4j:2.1.3") {
                        byConflictResolution("latest version of capability org.dom4j:dom4j")
                        byConflictResolution("between versions 2.1.3 and 1.6.1")
                    }
                }
                edge("jaxen:jaxen:1.1.1", "jaxen:jaxen:1.1.6") {
                    byConflictResolution("between versions 1.1.6 and 1.1.1")
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/14220#issuecomment-1967573024")
    def "first-level dependencies are retained after conflict resolution"() {

        mavenRepo.module("org.bouncycastle", "bcprov-jdk14", "1.70").publish()
        mavenRepo.module("org.bouncycastle", "bcprov-jdk18on", "1.71").publish()
        mavenRepo.module("org.bouncycastle", "bctls-fips", "1.0.9").publish()
        mavenRepo.module("org.bouncycastle", "bctls-jdk14", "1.70")
            .dependsOn(mavenRepo.module("org.bouncycastle", "bcprov-jdk14", "1.70").publish())
            .publish()
        mavenRepo.module("org.bouncycastle", "bctls-jdk18on", "1.72")
            .dependsOn(mavenRepo.module("org.bouncycastle", "bcprov-jdk18on", "1.72").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.bouncycastle:bcprov-jdk14:1.70")
                implementation("org.bouncycastle:bcprov-jdk18on:1.71")
                implementation("org.bouncycastle:bctls-fips:1.0.9")
                implementation("org.bouncycastle:bctls-jdk14:1.70")
                implementation("org.bouncycastle:bctls-jdk18on:1.72")
            }
        """

        capability("foo", "bcprov") {
            forModule("org.bouncycastle:bcprov-jdk14")
            forModule("org.bouncycastle:bcprov-jdk18on")
            selectHighest()
        }

        capability("foo", "bctls") {
            forModule("org.bouncycastle:bctls-fips")
            forModule("org.bouncycastle:bctls-jdk14")
            forModule("org.bouncycastle:bctls-jdk18on")
            selectHighest()
        }

        when:
        resolve.prepare()
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.bouncycastle:bcprov-jdk14:1.70", "org.bouncycastle:bcprov-jdk18on:1.72") {
                    byConflictResolution("between versions 1.72 and 1.71")
                }
                edge("org.bouncycastle:bcprov-jdk18on:1.71", "org.bouncycastle:bcprov-jdk18on:1.72")
                edge("org.bouncycastle:bctls-fips:1.0.9", "org.bouncycastle:bctls-jdk18on:1.72") {
                    byConflictResolution("latest version of capability foo:bctls")
                }
                edge("org.bouncycastle:bctls-jdk14:1.70", "org.bouncycastle:bctls-jdk18on:1.72")
                module("org.bouncycastle:bctls-jdk18on:1.72") {
                    module("org.bouncycastle:bcprov-jdk18on:1.72")
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/14220#issuecomment-1291618229")
    def "avoids corrupt serialized resolution result"() {

        mavenRepo.module("org.codehaus.woodstox", "wstx-asl", "4.0.6")
            .dependsOn(mavenRepo.module("org.codehaus.woodstox", "woodstox-core-asl", "4.0.6").publish())
            .publish()
        mavenRepo.module("javax.xml.stream", "stax-api", "1.0").publish()
        mavenRepo.module("org.codehaus.woodstox", "woodstox-core-asl", "4.4.1")
            .dependsOn(mavenRepo.module("javax.xml.stream", "stax-api", "1.0-2").publish())
            .publish()
        mavenRepo.module("stax", "stax-api", "1.0.1").publish()
        mavenRepo.module("woodstox", "wstx-asl", "2.9.3").publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.codehaus.woodstox:wstx-asl:4.0.6")
                implementation("javax.xml.stream:stax-api:1.0")
                implementation("org.codehaus.woodstox:woodstox-core-asl:4.4.1")
                implementation("stax:stax-api:1.0.1")
                implementation("woodstox:wstx-asl:2.9.3")
            }
        """

        capability("woodstox", "wstx-asl") {
            forModule("org.codehaus.woodstox:wstx-asl")
            forModule("org.codehaus.woodstox:woodstox-core-asl")
            selectHighest()
        }

        capability("stax", "stax-api") {
            forModule("javax.xml.stream:stax-api")
            selectHighest()
        }

        when:
        resolve.prepare()
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.codehaus.woodstox:wstx-asl:4.0.6", "org.codehaus.woodstox:woodstox-core-asl:4.4.1") {
                    byConflictResolution("latest version of capability woodstox:wstx-asl")
                    edge("javax.xml.stream:stax-api:1.0-2", "stax:stax-api:1.0.1") {
                        byConflictResolution("latest version of capability stax:stax-api")
                    }
                }
                edge("javax.xml.stream:stax-api:1.0", "stax:stax-api:1.0.1")
                module("org.codehaus.woodstox:woodstox-core-asl:4.4.1") {
                    byConflictResolution("between versions 4.4.1 and 4.0.6")
                }
                module("stax:stax-api:1.0.1") {
                    byConflictResolution("between versions 1.0-2 and 1.0.1")
                }
                edge("woodstox:wstx-asl:2.9.3", "org.codehaus.woodstox:woodstox-core-asl:4.4.1")
            }
        }
    }

    // region test fixtures

    class CapabilityClosure {

        private final String group
        private final String artifactId
        private final File buildFile

        CapabilityClosure(String group, String artifactId, File buildFile) {
            this.group = group
            this.artifactId = artifactId
            this.buildFile = buildFile
        }

        def forModule(String module) {
            buildFile << """
                dependencies.components.withModule('$module') {
                    allVariants {
                        withCapabilities {
                            addCapability('$group', '$artifactId', id.version)
                        }
                    }
                }
            """
        }

        def selectHighest() {
            buildFile << """
                configurations.runtimeClasspath {
                    resolutionStrategy {
                        capabilitiesResolution {
                            withCapability("$group:$artifactId") {
                                selectHighestVersion()
                            }
                        }
                    }
                }
            """
        }
    }

    def capability(String group, String module, @DelegatesTo(CapabilityClosure) Closure<?> closure) {
        def capabilityClosure = new CapabilityClosure(group, module, buildFile)
        closure.delegate = capabilityClosure
        closure.call(capabilityClosure)
    }

    // endregion
}
