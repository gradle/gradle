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

package org.gradle.api.internal.attributes

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Integration tests pinning the string form Gradle derives from a non-{@code String} attribute
 * value when it desugars one.
 * <p>
 * For a value implementing {@link org.gradle.api.Named} that form is {@code getName()}. That is the
 * representation attribute matching uses ({@code DefaultImmutableAttributesEntry#desugar}),
 * so it is the one a resolution result has to report for the reported value to describe the
 * match that actually happened. Publishing ({@code ModuleMetadataSpecBuilder#attributeValueFor}),
 * cache serialization ({@code DesugaringAttributeContainerSerializer}), transform inputs
 * ({@code AttributesToMapConverter}) and build-operation results
 * ({@code ResolveConfigurationResolutionBuildOperationResult}) all derive the same form.
 * <p>
 * A {@code Named} value created through {@code ObjectFactory.named} cannot distinguish
 * {@code getName()} from {@code toString()} — {@code NamedObjectInstantiator} generates both to
 * return the name. The tests here use an {@code enum ... implements Named} whose {@code getName()}
 * returns a value deliberately different from the {@code Enum.name()} that {@code toString()}
 * defaults to, which is what makes the two forms distinguishable at all.
 */
final class NamedAttributeDesugaringIntegrationTest extends AbstractIntegrationSpec {
    // region setup
    // An enum whose getName() is deliberately NOT its name(): getName() hyphenates and lowercases,
    // while toString() keeps Enum's default of returning name(). Any desugaring that reaches for
    // toString() instead of getName() yields TYPE_A where the Named contract says type-a.
    private static final String DIVERGENT_NAMED_ENUM = """
        enum MyType implements Named {
            TYPE_A, TYPE_B

            @Override
            String getName() { return name().toLowerCase().replace('_', '-') }
        }
    """

    private static final String ATTRIBUTE_DECL = """
        def ATTRIBUTE_TYPE = Attribute.of("myAttribute", MyType.class)
    """

    /**
     * Renders the resolution result's requested attributes at configuration time and prints them
     * at execution time. Rendering eagerly keeps a plain {@code List<String>} in the task, so the
     * task stays configuration-cache compatible. Both the value and the attribute type are
     * rendered: an un-desugared container would report the declared type rather than
     * {@code String}, and {@code getAttribute} would hand back the value itself, whose
     * {@code toString()} would silently stand in for the desugared form.
     */
    private static String reportRequestedAttributesTask() {
        return """
            tasks.register("report") {
                def requested = configurations.myResolver.incoming.resolutionResult.requestedAttributes
                def rendered = requested.keySet()
                    .findAll { it.name == "myAttribute" }
                    .collect { "Requested: myAttribute=" + requested.getAttribute(it) + " type=" + it.type.simpleName }
                doLast {
                    rendered.each { println(it) }
                }
            }
        """
    }
    // endregion setup

    def "resolution result reports a Named attribute value using getName()"() {
        given:
        buildFile("""
            ${DIVERGENT_NAMED_ENUM}
            ${ATTRIBUTE_DECL}

            configurations {
                resolvable("myResolver") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyType.TYPE_A) }
                }
            }

            ${reportRequestedAttributesTask()}
        """)

        when:
        succeeds("report")

        then:
        outputContains("Requested: myAttribute=type-a type=String")
    }

    def "published metadata and the resolution result agree on a Named attribute value"() {
        given:
        settingsFile("include 'consumer', 'producer'")

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            plugins { id 'maven-publish' }

            ${DIVERGENT_NAMED_ENUM}
            ${ATTRIBUTE_DECL}

            group = 'org.example'
            version = '1.0'

            def myVariant = configurations.consumable("myVariant") {
                attributes { attribute(ATTRIBUTE_TYPE, MyType.TYPE_A) }
                outgoing.artifact(file("output.txt"))
            }

            def component = publishing.softwareComponentFactory.adhoc("myComponent")
            component.addVariantsFromConfiguration(myVariant.get()) {
                mapToMavenScope("runtime")
            }
            components.add(component)

            publishing {
                repositories { maven { url = uri("${mavenRepo.uri}") } }
                publications {
                    maven(MavenPublication) { from components.myComponent }
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${DIVERGENT_NAMED_ENUM}
            ${ATTRIBUTE_DECL}

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyType.TYPE_A) }
                }
            }

            dependencies { myDeps(project(":producer")) }

            ${reportRequestedAttributesTask()}
        """)

        when:
        succeeds(":producer:publish", ":consumer:report")

        then: "the published variant attribute carries getName()"
        mavenRepo.module("org.example", "producer", "1.0")
            .parsedModuleMetadata
            .variant("myVariant")
            .attributes["myAttribute"] == "type-a"

        and: "the resolution result reports the same string, not Enum.name()"
        outputContains("Requested: myAttribute=type-a type=String")
    }

    def "resolution result reports a plain Enum attribute value using name()"() {
        // Guards the raw-enum path, deprecated at Attribute.of but supported until Gradle 10.
        // A plain enum has no getName(), so name() remains its desugared form.
        given:
        buildFile("""
            enum MyType { TYPE_A, TYPE_B }
            ${ATTRIBUTE_DECL}

            configurations {
                resolvable("myResolver") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyType.TYPE_A) }
                }
            }

            ${reportRequestedAttributesTask()}
        """)

        when:
        executer.noDeprecationChecks()
        succeeds("report")

        then:
        outputContains("Requested: myAttribute=TYPE_A type=String")
    }

    def "resolution result reports a plain Enum that overrides toString using name()"() {
        // name(), not toString(), is the form CoercingStringValueSnapshot coerces back via
        // Enum.valueOf, so an overridden toString() must not reach the desugared value.
        given:
        buildFile("""
            enum MyType {
                TYPE_A, TYPE_B

                @Override
                String toString() { return "not-the-constant-name" }
            }
            ${ATTRIBUTE_DECL}

            configurations {
                resolvable("myResolver") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyType.TYPE_A) }
                }
            }

            ${reportRequestedAttributesTask()}
        """)

        when:
        executer.noDeprecationChecks()
        succeeds("report")

        then:
        outputContains("Requested: myAttribute=TYPE_A type=String")
    }
}
