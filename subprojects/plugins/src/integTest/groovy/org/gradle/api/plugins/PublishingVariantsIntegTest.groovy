/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure

import static org.gradle.util.Matchers.containsText

class PublishingVariantsIntegTest extends AbstractIntegrationSpec {
    def "publishing variants with duplicate names fails"() {
        given:
        settingsFile << "rootProject.name = 'lib'"

        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            repositories {
                mavenLocal()
            }

            //def conf1 = configurations.create('sample1')
            //def conf2 = configurations.create('sample1')

            //components.java.withVariantsFromConfiguration(conf1) {
            //    skip()
            //}
            //components.java.withVariantsFromConfiguration(conf2)

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            group 'org.sample.SampleLib'
            """.stripIndent()

        file("src/main/java/org/sample/SampleLib.java") << """
            public class SampleLib {
                public void foo() {}
            }
            """.stripIndent()

        when:
        succeeds("outgoingVariants", "publishToMavenLocal")

        then:
        outputContains("Variant")
    }

    def "test variant guarantees"() {
        given:
        settingsFile << "rootProject.name = 'lib'"

        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            repositories {
                mavenLocal()
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            group 'org.sample.SampleLib'
            """.stripIndent()

        file("src/main/java/org/sample/SampleLib.java") << """
            public class SampleLib {
                public void foo() {}
            }
            """.stripIndent()

        expect:
        succeeds("build", "publishToMavenLocal")
    }

    def "test new variants are not publishable"() {
        given:
        settingsFile << "rootProject.name = 'lib'"

        buildFile << """
            import org.gradle.api.JavaVersion
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.artifacts.Configuration
            import org.gradle.api.attributes.Bundling
            import org.gradle.api.attributes.Category
            import org.gradle.api.attributes.LibraryElements
            import org.gradle.api.attributes.Usage
            import org.gradle.api.attributes.java.TargetJvmVersion
            import org.gradle.api.component.SoftwareComponentFactory
            import org.gradle.api.tasks.bundling.Jar
            import org.gradle.api.component.AdhocComponentWithVariants
            import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping

            import javax.inject.Inject

            class InstrumentedJarsPlugin implements Plugin<Project> {
                private final SoftwareComponentFactory softwareComponentFactory

                @Inject
                InstrumentedJarsPlugin(SoftwareComponentFactory softwareComponentFactory) {
                    this.softwareComponentFactory = softwareComponentFactory
                }

                @Override
                void apply(Project project) {
                    Configuration outgoingConfiguration = createOutgoingConfiguration(project, "myConf")
                    AdhocComponentWithVariants component = configurePublication(project, "myAdhocComponent", outgoingConfiguration)
                    //addVariantToExistingComponent(project, outgoingConfiguration, component)

                    AdhocComponentWithVariants java2 = softwareComponentFactory.adhoc("java2");
                    java2.addVariantsFromConfiguration(project.configurations.getByName("testResultsElementsForTest"), new JavaConfigurationVariantMapping("runtime", false));
                    project.getComponents().add(java2);
                }

                private Configuration createOutgoingConfiguration(Project project, String configurationName) {
                    project.configurations.create(configurationName) { Configuration cnf ->
                        cnf.canBeConsumed = true
                        cnf.canBeResolved = false
                        cnf.attributes {
                            it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                            it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                            it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                            it.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, JavaVersion.current().majorVersion.toInteger())
                            it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, configurationName + '-jar'))
                        }
                    }
                }

                private AdhocComponentWithVariants configurePublication(Project project, String componentName, Configuration outgoing) {
                    // create an adhoc component
                    def adhocComponent = softwareComponentFactory.adhoc(componentName)
                    // add it to the list of components that this project declares
                    project.components.add(adhocComponent)
                    // and register a variant for publication
                    adhocComponent.addVariantsFromConfiguration(outgoing) {
                        it.mapToMavenScope("runtime")
                    }
                }

                private void addVariantToExistingComponent(Project project, Configuration outgoing, AdhocComponentWithVariants component) {
                    component.addVariantsFromConfiguration(outgoing) {
                        // dependencies for this variant are considered runtime dependencies
                        it.mapToMavenScope("runtime")
                        // and also optional dependencies, because we don't want them to leak
                        it.mapToOptional()
                    }
                }
            }

            plugins {
                id 'java'
                id 'maven-publish'
            }

            apply plugin: InstrumentedJarsPlugin

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java2
                    }
                }
            }""".stripIndent()

        when:
        ExecutionFailure failure = fails("publishToMavenLocal")

        then:
        failure.assertHasCause("Cannot publish feature variant 'testResultsElementsForTest' as it is defined by unpublishable attributes: 'org.gradle.targetname, org.gradle.testsuitename, org.gradle.testsuitetype'")
    }
}
