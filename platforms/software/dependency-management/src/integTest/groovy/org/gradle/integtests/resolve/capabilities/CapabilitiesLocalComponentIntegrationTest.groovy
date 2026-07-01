/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class CapabilitiesLocalComponentIntegrationTest extends AbstractIntegrationSpec {

    def "can detect conflict between local projects providing the same capability"() {
        given:
        settingsFile << """
            rootProject.name = 'test'
            include 'b'
        """
        buildFile << """
            apply plugin: 'java-library'

            configurations.api.outgoing {
                capability 'org:capability:1.0'
            }

            dependencies {
                api project(":b")
            }

        """
        file('b/build.gradle') << """
            apply plugin: 'java-library'

            configurations.api.outgoing {
                capability 'test:b:unspecified'
                capability("org:capability:1.0")
            }
        """

        when:
        fails 'compileJava'

        then:
        failure.assertHasCause("""Module 'test:b' has been rejected:
   Cannot select module because of conflict with root project 'test' (compileClasspath). All provide capability 'org:capability:1.0'.""")
    }

    def 'fails to resolve undeclared test fixture'() {
        settingsFile << "rootProject.name = 'test'\n"
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(testFixtures(project(':')))
            }

            task resolve {
                doLast {
                    println configurations.compileClasspath.incoming.files.files
                }
            }
"""

        when:
        succeeds 'dependencyInsight', '--configuration', 'compileClasspath', '--dependency', ':'

        then:
        outputContains("Could not resolve root project 'test'.")
    }

    def "can lazily define and request capability"() {
        buildFile << """

            def value = "org:initial:1.0"

            configurations {
                consumable("conf") {
                    outgoing {
                        capability(project.provider(() -> value))
                    }
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "foo"))
                }
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "foo"))
                }
            }

            dependencies {
                deps(project()) {
                    capabilities {
                        requireCapability(project.provider(() -> value))
                    }
                }
            }

            value = "com:final:1.0"

            task resolve {
                def result = configurations.res.incoming.resolutionResult.rootComponent
                doLast {
                    def capabilities = result.get().dependencies.first().resolvedVariant.capabilities
                    assert capabilities.size() == 1
                    assert capabilities.first().group == "com"
                    assert capabilities.first().name == "final"
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "error when multiple capability selectors do not match includes both selectors"() {
        settingsFile << """
            include("other")
        """
        file("other/build.gradle") << """
            plugins {
                id("java-library")
            }
        """
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":other")) {
                    capabilities {
                        requireCapability("org:capability:1.0")
                        requireFeature("foo")
                    }
                }
            }

            task resolve {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    println(files*.name)
                }
            }
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Unable to find a variant of 'project ':other'' with the requested capabilities: ['org:capability', feature 'foo']")
    }

    @Issue("https://github.com/gradle/gradle/issues/26377")
    def "ResolvedVariantResults reported by ResolutionResult and ArtifactCollection have same capabilities when they are added to configuration in hierarchy"() {
        settingsFile << "include 'producer'"
        file("producer/build.gradle") << """
            group="com.foo"

            task zip(type: Zip) {
                archiveFileName = "producer.zip"
                destinationDirectory = layout.buildDirectory
            }

            configurations {
                dependencyScope("api") {
                    outgoing {
                        capability("com.foo:producer:2.0")
                        capability("org.bar:dependency-scope-capability:1.0")
                    }
                }
                consumable("elements") {
                    extendsFrom(api)
                    outgoing.artifact(tasks.zip)
                    outgoing.capability("org.bar:consumable-capability:1.0")
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY))
                    }
                }
            }
        """

        buildFile << """
            configurations {
                dependencyScope("implementation")
                resolvable("classpath") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY))
                    }
                }
            }

            dependencies {
                implementation(project(":producer"))
            }

            task resolve {
                def conf = configurations.classpath
                def root = conf.incoming.resolutionResult.root
                def artifactVariants = conf.incoming.artifacts.artifacts.collect { it.variant }
                doLast {
                    def graphVariant = root.dependencies.find { it.selected.id.projectPath == ":producer" }.resolvedVariant
                    def artifactVariant = artifactVariants.find { it.owner.projectPath == ":producer" }
                    assert graphVariant.capabilities == artifactVariant.capabilities

                    def expected = ["com.foo:producer:2.0", "org.bar:dependency-scope-capability:1.0", "org.bar:consumable-capability:1.0"] as Set
                    assert graphVariant.capabilities.collect { "\${it.group}:\${it.name}:\${it.version}" } as Set == expected
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "can request capability without version"() {
        settingsFile << """
            include("other")
        """
        file("other/build.gradle") << """
            configurations {
                consumable("apiElements") {
                    outgoing {
                        artifact(file("foo.txt"))
                        capability("org:capability:1.0")
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY))
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":other")) {
                    capabilities {
                        requireCapability("org:capability")
                    }
                }
            }

            task resolve {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    println files*.name
                }
            }
        """

        expect:
        succeeds("resolve")
        outputContains("[foo.txt]")
    }

    def "useful error message when target component has matching capability but incorrect attributes"() {
        settingsFile << """
            include(":other")
        """
        file("other/build.gradle") << """
            configurations.consumable("elements") {
                outgoing {
                    ${requested.collect { "capability('$it:1.0')" }.join("\n")}
                }
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                }
            }
        """

        buildFile << """
            def conf = configurations.create("res") {
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "bar"))
                }
            }

            dependencies {
                res(project(":other")) {
                    capabilities {
                        ${ requested.collect { "requireCapability('$it')" }.join("\n") }
                    }
                }
            }

            tasks.register("resolve") {
                dependsOn(conf)
            }
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("No matching variant of project ':other' $description was found")
        failure.assertHasErrorOutput("Incompatible because this component declares attribute 'org.gradle.category' with value 'foo' and the consumer needed attribute 'org.gradle.category' with value 'bar'")

        where:
        requested               | description
        ["org:foo"]             | "with capability 'org:foo'"
        ["org:foo", "org:bar"]  | "with capabilities ['org:bar', 'org:foo']"
    }

    def "requireCapability() narrows otherwise-ambiguous variants when capability differs in group or name"() {
        given:
        setupVariantsDistinguishedByCapability("org.example:c1:1.0", "org.example:c2:1.0")

        expect:
        succeeds "forceResolution"
    }

    def "requireCapability() does NOT narrow when notations differ only in the version segment, and the error message surfaces the effective selector"() {
        given:
        setupVariantsDistinguishedByCapability("group:name:1.0", "group:name:2.0")

        when:
        fails "forceResolution"

        then: "the ambiguity is reported with the effective (version-stripped) capability and the trailer indicates required capability was considered"
        assertFullMessageCorrect("""Required by:
         root project 'test'
      > The consumer was configured to find attribute 'custom' with value 'a1' and capability 'group:name'. However we cannot choose between the following variants of project ':producer':
          - v1
          - v2
        All of them match the consumer attributes and required capability:
          - Variant 'v1' capability 'group:name:1.0' declares attribute 'custom' with value 'a1'
          - Variant 'v2' capability 'group:name:2.0' declares attribute 'custom' with value 'a1'""")
    }

    private void setupVariantsDistinguishedByCapability(String c1Notation, String c2Notation) {
        settingsFile << """
            include("producer")
            rootProject.name = "test"
        """

        file("producer/build.gradle") << """
            group = "org.example"
            version = "1.0"

            def custom = Attribute.of("custom", String)

            configurations {
                consumable("v1") {
                    attributes {
                        attribute(custom, "a1")
                    }
                    outgoing {
                        capability("${c1Notation}")
                    }
                }
                consumable("v2") {
                    attributes {
                        attribute(custom, "a1")
                    }
                    outgoing {
                        capability("${c2Notation}")
                    }
                }
            }
        """

        buildFile << """
            def custom = Attribute.of("custom", String)

            configurations {
                dependencyScope("myDependencies")
                resolvable("resolveMe") {
                    extendsFrom(myDependencies)
                }
            }

            dependencies {
                myDependencies(project(":producer")) {
                    attributes {
                        attribute(custom, "a1")
                    }
                    capabilities {
                        requireCapability("${c1Notation}")
                    }
                }
            }

            task forceResolution {
                def files = configurations.resolveMe.incoming.files
                doLast {
                    files.each { println(it) }
                }
            }
        """
    }

    private void assertFullMessageCorrect(String identifyingFragment) {
        identifyingFragment.eachLine {
            failure.assertHasErrorOutput(it.trim())
        }
    }
}
