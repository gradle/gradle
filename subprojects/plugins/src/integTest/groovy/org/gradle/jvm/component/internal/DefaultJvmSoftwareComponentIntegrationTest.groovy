/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.jvm.component.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests {@link DefaultJvmSoftwareComponent}.
 */
class DefaultJvmSoftwareComponentIntegrationTest extends AbstractIntegrationSpec {

    def "cannot instantiate component instances without the java base plugin applied"() {
        given:
        buildFile << """
            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent)
            }
        """

        expect:
        fails "help"
        result.assertHasErrorOutput("The java-base plugin must be applied in order to create instances of DefaultJvmSoftwareComponent.")
    }

    def "can instantiate components and features with java-base plugin applied in Groovy DSL"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature)
                    }
                }
                comp2(JvmSoftwareComponent) {
                    features {
                        feat2(JvmFeature)
                    }
                }
            }

            task verify {
                assert components.comp instanceof DefaultJvmSoftwareComponent
                assert sourceSets.feat

                assert components.comp2 instanceof DefaultJvmSoftwareComponent
                assert sourceSets.feat2
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate component and features with java-base plugin applied in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            plugins {
                id("java-base")
            }

            ${factoryRegistrationKotlin()}

            components {
                create<JvmSoftwareComponent>("comp") {
                    features {
                        create<JvmFeature>("feat")
                    }
                }
                create<JvmSoftwareComponent>("comp2") {
                    features {
                        create<JvmFeature>("feat2")
                    }
                }
            }

            tasks.register("verify") {
                assert(components.named("comp").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("feat").isPresent())

                assert(components.named("comp2").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("feat2").isPresent())
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate component instances adjacent to java component with java-library plugin applied in Groovy DSL"() {
        given:
        buildFile << """
            plugins {
                id('java-library')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature)
                    }
                }
            }

            task verify {
                assert components.java instanceof DefaultJvmSoftwareComponent
                assert sourceSets.main

                assert components.comp instanceof DefaultJvmSoftwareComponent
                assert sourceSets.feat
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate component instances adjacent to java component with java-library plugin applied in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            ${factoryRegistrationKotlin()}

            components {
                create<JvmSoftwareComponent>("comp") {
                    features {
                        create<JvmFeature>("feat")
                    }
                }
            }

            tasks.register("verify") {
                assert(components.named("java").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("main").isPresent())

                assert(components.named("comp").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("feat").isPresent())
            }
        """

        expect:
        succeeds "verify"
    }

    def "can configure main feature in java component added by java-library plugin in Groovy DSL"() {
        given:
        buildFile << """
            plugins {
                id('java-library')
            }

            ${importStatements()}

            components {
                java {
                    features.main {
                        withJavadocJar()
                    }
                }
            }

            task verify {
                assert components.java instanceof DefaultJvmSoftwareComponent
                assert configurations.javadocElements
                assert sourceSets.main
            }
        """

        expect:
        succeeds "verify"
    }

    // TODO: Eventually we want accessors to be generated for the `java` component so that
    // we can use `java {}` instead of `named<JvmSoftwareComponent>("java") {}`.
    def "can configure main feature in java component added by java-library plugin in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            ${importStatements()}

            components {
                named<JvmSoftwareComponent>("java") {
                    features.named<JvmFeature>("main") {
                        withJavadocJar()
                    }
                }
            }

            tasks.register("verify") {
                assert(components.named("java").get() is DefaultJvmSoftwareComponent)
                assert(configurations.named("javadocElements").isPresent())
                assert(sourceSets.named("main").isPresent())
            }
        """

        expect:
        succeeds "verify"
    }

    def "component contains no features by default"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent)
            }

            task verify {
                assert components.comp
                assert components.comp.features.empty
                assert components.comp.variants.empty
            }
        """

        expect:
        succeeds "verify"
    }

    def "can add features to component"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature)
                    }
                }
            }

            task verify {
                assert components.comp.features.collect { it.name } == ['feat']
                assert components.comp.features.feat.variants.collect { it.name } == ['featApiElements', 'featRuntimeElements']
                assert components.comp.variants.collect { it.name } == ['featApiElements', 'featRuntimeElements']
            }
        """

        expect:
        succeeds "verify"
    }

    def "features can access variants which are added by default"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature) {
                            variants {
                                featApiElements {
                                    assert attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_API
                                }
                                featRuntimeElements {
                                    assert attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
                                }
                            }
                        }
                    }
                }
            }
        """

        expect:
        succeeds "help"
    }

    def "dynamic variants of features are accessible from the component"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature) {
                            withJavadocJar()
                            withSourcesJar()
                        }
                    }
                }
            }

            task verify {
                assert components.comp
                assert components.comp.features.collect { it.name } == ['feat']
                assert components.comp.features.feat.variants.collect { it.name } == ['featApiElements', 'featJavadocElements', 'featRuntimeElements', 'featSourcesElements']
                assert components.comp.variants.collect { it.name } == ['featApiElements', 'featJavadocElements', 'featRuntimeElements', 'featSourcesElements']
            }
        """

        expect:
        succeeds "verify"
    }

    def "component exposes variants from multiple features"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat1(JvmFeature) {
                            withJavadocJar()
                            withSourcesJar()
                        }
                        feat2(JvmFeature)
                    }
                }
            }

            task verify {
                assert components.comp
                assert components.comp.features.collect { it.name } == ['feat1', 'feat2']
                assert components.comp.features.feat1.variants.collect { it.name } == ['feat1ApiElements', 'feat1JavadocElements', 'feat1RuntimeElements', 'feat1SourcesElements']
                assert components.comp.features.feat2.variants.collect { it.name } == ['feat2ApiElements', 'feat2RuntimeElements']
                assert components.comp.variants.collect { it.name } == ['feat1ApiElements', 'feat1JavadocElements', 'feat1RuntimeElements', 'feat1SourcesElements', 'feat2ApiElements', 'feat2RuntimeElements']
            }
        """

        expect:
        succeeds "verify"
    }

    def "can configure custom variants for single target features"() {
        mavenRepo.module("org", "foo", "1.0").publish()

        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}
            repositories { maven { url '${mavenRepo.uri}' } }

            configurations {
                customConfiguration
            }

            dependencies {
                customConfiguration 'org:foo:1.0'
            }

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature) {
                            variants {
                                customRuntimeElements(ConfigurationBackedConsumableVariant) {
                                    configuration {
                                        extendsFrom configurations.customConfiguration
                                    }
                                }
                            }
                        }
                    }
                }
            }

            task verify {
                assert components.comp
                assert components.comp.features.feat.variants.collect { it.name } == ['customRuntimeElements', 'featApiElements', 'featRuntimeElements']
                assert components.comp.variants.collect { it.name } == ['customRuntimeElements', 'featApiElements', 'featRuntimeElements']

                assert configurations.customRuntimeElements.allDependencies.collect { it.group + ":" + it.name + ":" + it.version } == ['org:foo:1.0']
            }
        """

        expect:
        succeeds "verify"
    }

    def "custom variants can access configurations from parent feature"() {
        mavenRepo.module("org", "foo", "1.0").publish()

        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}
            repositories { maven { url '${mavenRepo.uri}' } }

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature) {
                            variants {
                                customRuntimeElements(ConfigurationBackedConsumableVariant) {
                                    configuration {
                                        extendsFrom implementationConfiguration
                                    }
                                }
                            }
                        }
                    }
                }
            }

            dependencies {
                featImplementation 'org:foo:1.0'
            }

            task verify {
                assert configurations.customRuntimeElements.allDependencies.collect { it.group + ":" + it.name + ":" + it.version } == ['org:foo:1.0']
            }
        """

        expect:
        succeeds "verify"
    }

    def "configuration state is accessible through wrapping variant"() {
        mavenRepo.module("org", "foo", "1.0").publish()

        given:
        settingsFile << "rootProject.name = 'proj'"
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}
            repositories { maven { url '${mavenRepo.uri}' } }

            configurations {
                customConfiguration
            }

            dependencies {
                customConfiguration 'org:foo:1.0'
                constraints {
                    customConfiguration 'org:baz:1.0'
                }
            }

            task myJar(type: Jar)

            group = 'group'
            version = '1.1'

            components {
                comp(JvmSoftwareComponent) {
                    features {
                        feat(JvmFeature) {
                            variants {
                                customRuntimeElements(ConfigurationBackedConsumableVariant) {
                                    configuration {
                                        extendsFrom configurations.customConfiguration
                                        attributes {
                                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                                        }
                                        outgoing.artifact(myJar)
                                        outgoing.capability("com:foo:1.0")
                                    }
                                    assert dependencies.collect { it.group + ":" + it.name + ":" + it.version } == ['org:foo:1.0']
                                    assert dependencyConstraints.collect { it.group + ":" + it.name + ":" + it.version } == ['org:baz:1.0']
                                    assert attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
                                    assert artifacts.files == [tasks.myJar.archiveFile.get().asFile] as Set
                                    assert capabilities.capabilities.collect { it.group + ":" + it.name + ":" + it.version } == ['group:proj-feat:1.1', 'com:foo:1.0']
                                }
                            }
                        }
                    }
                }
            }
        """

        expect:
        succeeds "help"
    }

    private static final String factoryRegistrationGroovy() {
        """
            ${importStatements()}

            components {
                registerFactory(JvmSoftwareComponent) {
                    objects.newInstance(DefaultJvmSoftwareComponent, it)
                }
            }
        """
    }

    private static final String factoryRegistrationKotlin() {
        """
            ${importStatements()}

            components {
                registerFactory(JvmSoftwareComponent::class.java) {
                    objects.newInstance(DefaultJvmSoftwareComponent::class.java, it)
                }
            }
        """
    }

    /**
     * Since JvmSoftwareComponent has no non-internal type, we always need to import it.
     */
    private static final String importStatements() {
        """
            import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent
        """
    }
}
