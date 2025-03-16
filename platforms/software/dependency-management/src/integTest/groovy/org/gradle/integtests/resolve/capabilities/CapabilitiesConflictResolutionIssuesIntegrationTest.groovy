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

    @Issue("https://github.com/gradle/gradle/issues/30969")
    def "dependency may have same capability as its transitive dependency and fails with rejection without capability resolution rule"() {
        mavenRepo.module("org.hamcrest", "hamcrest-core", "2.2")
            .dependsOn(mavenRepo.module("org.hamcrest", "hamcrest", "2.2").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.hamcrest:hamcrest-core:2.2")
            }

            dependencies.components.withModule('org.hamcrest:hamcrest-core') {
                allVariants {
                    withCapabilities {
                        addCapability('org.hamcrest', 'hamcrest', id.version)
                    }
                }
            }
        """

        when:
        resolve.prepare()
        fails(":checkDeps")

        then:
        failure.assertHasCause("Could not resolve org.hamcrest:hamcrest-core:2.2")
        failure.assertHasCause("Module 'org.hamcrest:hamcrest-core' has been rejected")
        failure.assertHasErrorOutput("Cannot select module with conflict on capability 'org.hamcrest:hamcrest:2.2' also provided by [org.hamcrest:hamcrest:2.2(runtime)]")
        failure.assertHasCause("Module 'org.hamcrest:hamcrest' has been rejected")
        failure.assertHasErrorOutput("Cannot select module with conflict on capability 'org.hamcrest:hamcrest:2.2' also provided by [org.hamcrest:hamcrest-core:2.2(runtime)]")
    }

    @Issue("https://github.com/gradle/gradle/issues/30969")
    def "dependency may have same capability as its transitive dependency"() {
        mavenRepo.module("org.hamcrest", "hamcrest-core", "2.2")
            .dependsOn(mavenRepo.module("org.hamcrest", "hamcrest", "2.2").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.hamcrest:hamcrest-core:2.2")
            }
        """

        capability("org.hamcrest", "hamcrest") {
            forModule("org.hamcrest:hamcrest-core")
            selectModule("org.hamcrest", winner)
        }

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        if (winner == "hamcrest-core") {
            resolve.expectGraph {
                root(":", ":test:") {
                    module("org.hamcrest:hamcrest-core:2.2") {
                        edge("org.hamcrest:hamcrest:2.2", "org.hamcrest:hamcrest-core:2.2")
                        byConflictResolution('Explicit selection of org.hamcrest:hamcrest-core:2.2 variant runtime')
                    }
                }
            }
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org.hamcrest:hamcrest-core:2.2", "org.hamcrest:hamcrest:2.2") {
                        notRequested()
                        byConflictResolution('Explicit selection of org.hamcrest:hamcrest:2.2 variant runtime')
                    }
                }
            }
        }

        where:
        winner << ["hamcrest-core", "hamcrest"]
    }

    @Issue("https://github.com/gradle/gradle/issues/30969")
    def "dependency may have same capability as its distant transitive dependency"() {
        mavenRepo.module("org", "parent", "2.2").dependsOn(
            mavenRepo.module("org", "middle", "2.2").dependsOn(
                mavenRepo.module("org", "child", "2.2").publish()
            ).publish()
        ).publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org:parent:2.2")
            }
        """

        capability("org", "child") {
            forModule("org:parent")
            selectModule("org", winner)
        }

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        if (winner == "parent") {
            resolve.expectGraph {
                root(":", ":test:") {
                    module("org:parent:2.2") {
                        module("org:middle:2.2") {
                            edge("org:child:2.2", "org:parent:2.2") {
                                byConflictResolution('Explicit selection of org:parent:2.2 variant runtime')
                            }
                        }
                    }
                }
            }
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:parent:2.2", "org:child:2.2") {
                        notRequested()
                        byConflictResolution('Explicit selection of org:child:2.2 variant runtime')
                    }
                }
            }
        }

        where:
        winner << ["parent", "child"]
    }

    @Issue("https://github.com/gradle/gradle/issues/30969")
    def "parent child capability conflict can also conflict with a third node"() {
        mavenRepo.module("org", "A")
            .dependsOn(mavenRepo.module("org", "B").publish())
            .publish()

        mavenRepo.module("org", "x")
            .dependsOn(mavenRepo.module("org", "y")
                .dependsOn(mavenRepo.module("org", "C").publish())
                .publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org:A:1.0")
                implementation("org:x:1.0")
            }
        """

        capability("org", "capability") {
            forModule("org:A")
            forModule("org:B")
            forModule("org:C")
            withResolutionRule(rule)
        }

        when:
        resolve.prepare()
        succeeds("checkDeps")

        then:
        if (winner == "A") {
            resolve.expectGraph {
                root(":", ":test:") {
                    module("org:A:1.0") {
                        edge("org:B:1.0", "org:A:1.0") {
                            byConflictResolution("Explicit selection of org:A:1.0 variant runtime")
                        }
                    }
                    module("org:x:1.0") {
                        module("org:y:1.0") {
                            edge("org:C:1.0", "org:A:1.0")
                        }
                    }
                }
            }
        } else if (winner == "B") {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:A:1.0", "org:B:1.0") {
                        notRequested()
                        byConflictResolution("Explicit selection of org:B:1.0 variant runtime")
                    }
                    module("org:x:1.0") {
                        module("org:y:1.0") {
                            edge("org:C:1.0", "org:B:1.0")
                        }
                    }
                }
            }
        } else if (winner == "C") {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:A:1.0", "org:C:1.0") {
                        byConflictResolution("Explicit selection of org:C:1.0 variant runtime")
                    }
                    module("org:x:1.0") {
                        module("org:y:1.0") {
                            module("org:C:1.0")
                        }
                    }
                }
            }
        }

        where:
        rule                                                            | winner
        [[group: "org", module: "A"]]                                   | "A"
        [[group: "org", module: "B"], [group: "org", module: "A"]]      | "B"
        [[group: "org", module: "C"]]                                   | "C"
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
        succeeds(":p1:checkDeps", "-s")

        then:
        resolve.expectGraph {
            root(":p1", "test:p1:") {
                project(":p2", "test:p2:") {
                    configuration 'runtimeElements'
                    project(":shared", "test:shared:") {
                        artifact(classifier: 'one-preferred')
                        byConflictResolution("Explicit selection of project :shared variant onePrefRuntimeElements")
                    }
                    project(":shared", "test:shared:") {
                        artifact(classifier: 'two-preferred')
                        byConflictResolution("Explicit selection of project :shared variant twoPrefRuntimeElements")
                    }
                }
                project(":shared", "test:shared:") {
                    variant('onePrefRuntimeElements', [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': "${JavaVersion.current().majorVersion}",
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-runtime'])
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
        succeeds(":checkDeps")

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
                implementation("org.bouncycastle:bctls-fips:1.0.9")
                implementation("org.bouncycastle:bctls-jdk14:1.70")
                implementation("org.bouncycastle:bctls-jdk18on:1.72")
                implementation("org.bouncycastle:bcprov-jdk14:1.70")
                implementation("org.bouncycastle:bcprov-jdk18on:1.71")
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
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.bouncycastle:bcprov-jdk14:1.70", "org.bouncycastle:bcprov-jdk18on:1.72") {
                    byConflictResolution("between versions 1.72 and 1.71")
                    byConflictResolution("latest version of capability foo:bcprov")
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
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.codehaus.woodstox:wstx-asl:4.0.6", "org.codehaus.woodstox:woodstox-core-asl:4.4.1") {
                    byConflictResolution("latest version of capability woodstox:wstx-asl")
                    edge("javax.xml.stream:stax-api:1.0-2", "stax:stax-api:1.0.1")
                }
                edge("javax.xml.stream:stax-api:1.0", "stax:stax-api:1.0.1")
                module("org.codehaus.woodstox:woodstox-core-asl:4.4.1") {
                    byConflictResolution("latest version of capability woodstox:wstx-asl")
                }
                module("stax:stax-api:1.0.1") {
                    byConflictResolution("latest version of capability stax:stax-api")
                }
                edge("woodstox:wstx-asl:2.9.3", "org.codehaus.woodstox:woodstox-core-asl:4.4.1")
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/14220#issuecomment-1423804572")
    def "resolution succeeds when nodes of a conflict become deselected before conflict is resolved"() {

        mavenRepo.module("org.hibernate", "hibernate-core", "5.4.18.Final")
            .dependsOn("org.dom4j", "dom4j", "2.1.3", null, "compile", null, [[group: "*", module: "*"]])
            .publish()
        mavenRepo.module("org.dom4j", "dom4j", "2.1.3").publish()
            .dependsOn(mavenRepo.module("jaxen", "jaxen", "1.1.6").publish())
            .publish()
        mavenRepo.module("jaxen", "jaxen", "1.1.1")
            .dependsOn(mavenRepo.module("dom4j", "dom4j", "1.6.1").publish())
            .publish()
        mavenRepo.module("org.unitils", "unitils-dbmaintainer", "3.3")
            .dependsOn(mavenRepo.module("org.hibernate", "hibernate", "3.2.5.ga").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.hibernate:hibernate-core:5.4.18.Final")
                implementation("jaxen:jaxen:1.1.1")
                implementation("org.unitils:unitils-dbmaintainer:3.3")
            }
        """

        capability("org.dom4j", "dom4j") {
            forModule("dom4j:dom4j")
            selectHighest()
        }
        capability("org.hibernate", "hibernate-core") {
            forModule("org.hibernate:hibernate")
            selectHighest()
        }

        when:
        resolve.prepare()
        succeeds(":checkDeps", "-s")

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
                module("org.unitils:unitils-dbmaintainer:3.3") {
                    edge("org.hibernate:hibernate:3.2.5.ga", "org.hibernate:hibernate-core:5.4.18.Final") {
                        byConflictResolution("latest version of capability org.hibernate:hibernate-core")
                    }
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/26145")
    @Issue("https://github.com/ljacomet/logging-capabilities/issues/33")
    def "resolution succeeds when two conflicts affect a single node"() {

        mavenRepo.module("org.slf4j", "slf4j-log4j12", "1.5.6").publish()
        mavenRepo.module("org.slf4j", "log4j-over-slf4j", "1.4.2").publish()
        mavenRepo.module("ch.qos.logback", "logback-classic", "1.3.11").publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("ch.qos.logback:logback-classic:1.3.11")
                implementation("org.slf4j:slf4j-log4j12:1.5.6")
                implementation("org.slf4j:log4j-over-slf4j:1.4.2")
            }
        """

        capability("slf4j-impl", "slf4j-impl") {
            forModule("org.slf4j:slf4j-log4j12")
            forModule("ch.qos.logback:logback-classic")
            selectModule("ch.qos.logback", "logback-classic")
        }

        capability("slf4j-vs-log4j", "slf4j-vs-log4j") {
            forModule("org.slf4j:slf4j-log4j12")
            forModule("org.slf4j:log4j-over-slf4j")
            selectModule("org.slf4j", "log4j-over-slf4j")
        }

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("ch.qos.logback:logback-classic:1.3.11")
                edge("org.slf4j:slf4j-log4j12:1.5.6", "ch.qos.logback:logback-classic:1.3.11") {
                    byConflictResolution("Explicit selection of ch.qos.logback:logback-classic:1.3.11 variant runtime")
                }
                module("org.slf4j:log4j-over-slf4j:1.4.2")
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/29208")
    def "valid graph with both module conflict and capability conflict"() {
        mavenRepo.module("org.bouncycastle", "bcprov-jdk12", "130").publish()
        mavenRepo.module("org.bouncycastle", "bcprov-jdk18on", "1.71").publish()
        mavenRepo.module("org.bouncycastle", "bcpkix-jdk18on", "1.72")
            .dependsOn(mavenRepo.module("org.bouncycastle", "bcprov-jdk18on", "1.72").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.bouncycastle:bcprov-jdk12:130")
                implementation("org.bouncycastle:bcprov-jdk18on:1.71")
                implementation("org.bouncycastle:bcpkix-jdk18on:1.72")
            }
        """

        capability("org.gradlex", "bouncycastle-bcprov") {
            forModule("org.bouncycastle:bcprov-jdk12")
            forModule("org.bouncycastle:bcprov-jdk18on")
            selectHighest()
        }

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.bouncycastle:bcprov-jdk12:130")
                edge("org.bouncycastle:bcprov-jdk18on:1.71", "org.bouncycastle:bcprov-jdk12:130") {
                    byConflictResolution("latest version of capability org.gradlex:bouncycastle-bcprov")
                }
                module("org.bouncycastle:bcpkix-jdk18on:1.72") {
                    edge("org.bouncycastle:bcprov-jdk18on:1.72", "org.bouncycastle:bcprov-jdk12:130")
                }
            }
        }
    }

    @Issue("https://github.com/ljacomet/logging-capabilities/issues/20")
    def "resolving a conflict does not depend on participant order"() {
        given:
        mavenRepo.module("org", "testA", "1.0").publish()
        mavenRepo.module("org", "testB", "1.0").publish()
        mavenRepo.module("org", "testC", "1.0").publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                ${deps.collect { "implementation('$it')" }.join("\n")}
            }
        """

        capability("org.test", "cap") {
            forModule("org:testA")
            forModule("org:testB")
            forModule("org:testC")
            selectModule("org", "testC")
        }

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:testA:1.0', 'org:testC:1.0') {
                    byConflictResolution("Explicit selection of org:testC:1.0 variant runtime")
                }
                edge('org:testB:1.0', 'org:testC:1.0') {
                    byConflictResolution("Explicit selection of org:testC:1.0 variant runtime")
                }
                module('org:testC:1.0')
            }
        }

        where:
        deps << ['org:testA:1.0', 'org:testB:1.0', 'org:testC:1.0'].permutations()
    }

    @Issue("https://github.com/gradle/gradle/pull/29993")
    def "does not generate stale conflicts"() {

        mavenRepo.module("org.apache.sshd", "sshd-common", "2.12.1").publish()
            .pomFile.text = '''<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.apache.sshd</groupId>
    <artifactId>sshd-common</artifactId>
    <version>2.12.1</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.apache.httpcomponents</groupId>
                <artifactId>httpclient</artifactId>
                <version>4.5.14</version>
                <exclusions>
                    <exclusion>
                        <groupId>commons-logging</groupId>
                        <artifactId>commons-logging</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>jcl-over-slf4j</artifactId>
            <version>1.7.32</version>
        </dependency>
     </dependencies>
</project>
'''

        mavenRepo.module("org.slf4j", "jcl-over-slf4j", "1.7.32").publish()

        mavenRepo.module("org.apache.httpcomponents", "httpclient", "4.3.2").publish()
            .dependsOn(mavenRepo.module("commons-logging", "commons-logging", "1.1.3").publish())
            .publish()
        mavenRepo.module("org.apache.httpcomponents", "httpclient", "4.5.14").publish()
            .dependsOn(mavenRepo.module("commons-logging", "commons-logging", "1.2").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                constraints {
                    api("org.apache.sshd:sshd-common:2.12.1")
                }
                implementation(platform("org.apache.sshd:sshd-common"))
                implementation("org.apache.sshd:sshd-common")

                implementation("org.apache.httpcomponents:httpclient:4.3.2")
                implementation("commons-logging:commons-logging:1.1.3")
                implementation("org.apache.httpcomponents:httpclient:4.5.14")
            }
        """

        capability("org.gradle.internal.capability", "commons-logging") {
            forModule("commons-logging:commons-logging")
            forModule("commons-logging:commons-logging-api")
            forModule("org.slf4j:jcl-over-slf4j")
            selectModule("org.slf4j", "jcl-over-slf4j")
        }

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint('org.apache.sshd:sshd-common:2.12.1', 'org.apache.sshd:sshd-common:2.12.1') {
                    byConstraint()
                }
                edge('org.apache.sshd:sshd-common', 'org.apache.sshd:sshd-common:2.12.1') {
                    module('org.slf4j:jcl-over-slf4j:1.7.32') {
                        byConflictResolution("Explicit selection of org.slf4j:jcl-over-slf4j:1.7.32 variant runtime")
                    }
                }
                edge('org.apache.sshd:sshd-common', 'org.apache.sshd:sshd-common:2.12.1') {
                    constraint('org.apache.httpcomponents:httpclient:4.5.14')
                }
                edge('org.apache.httpcomponents:httpclient:4.3.2', 'org.apache.httpcomponents:httpclient:4.5.14') {
                    byConstraint()
                    byConflictResolution("between versions 4.5.14 and 4.3.2")
                }
                edge('commons-logging:commons-logging:1.1.3', 'org.slf4j:jcl-over-slf4j:1.7.32')
                module("org.apache.httpcomponents:httpclient:4.5.14")
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

        def selectModule(String selectedGroup, String selectedModule) {
            buildFile << """
                configurations.runtimeClasspath {
                    resolutionStrategy {
                        capabilitiesResolution {
                            withCapability("$group:$artifactId") {
                                def result = candidates.find {
                                    it.id.group == "${selectedGroup}" && it.id.module == "${selectedModule}"
                                }
                                assert result != null
                                select(result)
                            }
                        }
                    }
                }
            """
        }

        def withResolutionRule(List<Map<String, String>> order) {
            buildFile << """
                configurations.runtimeClasspath {
                    resolutionStrategy {
                        capabilitiesResolution {
                            withCapability("$group:$artifactId") {
                                def result = null
            """

            order.each { winner ->
                def group = winner.group
                def module = winner.module
                buildFile << """
                                result = candidates.find {
                                    it.id.group == "${group}" && it.id.module == "${module}"
                                }
                                if (result != null) {
                                    select(result)
                                    return
                                }
                """
            }

            buildFile << """
                                assert result != null
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
