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

package org.gradle.api.publish.maven

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

class MavenPublishExternalVariantIntegrationTest extends AbstractMavenPublishIntegTest {

    def "publishes resolved jvm coordinates for multi-coordinates external module dependency"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }
            ${mavenCentralRepository()}
            dependencies {
                implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2"
            }

            components.java.withVariantsFromConfiguration(configurations.runtimeElements) {
                dependencyMapping {
                    publishResolvedCoordinates = true
                    resolutionConfiguration = configurations.runtimeClasspath
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        groupId = "org"
                        artifactId = "runtimeElements"
                        version = "1.0"
                        from components.java
                    }
                }
            }
        """
        def repoModule = javaLibrary(mavenRepo.module('org', "runtimeElements", '1.0'))

        when:
        succeeds "publish"

        then:
        // POM uses resolved variant coordinates
        def dependencies = repoModule.parsedPom.scopes.runtime.dependencies
        dependencies.size() == 1
        def dependency = dependencies.values().first()
        dependency.groupId == "org.jetbrains.kotlinx"
        dependency.artifactId == "kotlinx-coroutines-core-jvm"
        dependency.version == "1.7.2"

        // GMM continues to use component coordinates
        def gmmDependencies = repoModule.parsedModuleMetadata.variant("runtimeElements").dependencies
        gmmDependencies.size() == 1
        def gmmDependency = gmmDependencies.first()
        gmmDependency.group == "org.jetbrains.kotlinx"
        gmmDependency.module == "kotlinx-coroutines-core"
        gmmDependency.version == "1.7.2"
    }

    def "publishes resolved non-jvm coordinates for multi-coordinates external module dependency"() {
        given:
        buildFile << """
            plugins {
                id 'maven-publish'
            }

            configurations {
                dependencyScope("implementation")
                resolvable("runtimeClasspath") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "library"))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "kotlin-api"))
                        attribute(Attribute.of("org.jetbrains.kotlin.native.target", String), "ios_x64")
                        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String), "native")
                    }
                }
                consumable("runtimeElements") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "library"))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "kotlin-api"))
                        attribute(Attribute.of("org.jetbrains.kotlin.native.target", String), "ios_x64")
                        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String), "native")
                    }
                }
            }

            ${mavenCentralRepository()}
            dependencies {
                implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2"
            }
            interface MyServices {
                @Inject
                SoftwareComponentFactory getSoftwareComponentFactory()
            }
            def factory = objects.newInstance(MyServices).softwareComponentFactory
            def comp = factory.adhoc("comp")
            comp.addVariantsFromConfiguration(configurations.runtimeElements) {
                dependencyMapping {
                    publishResolvedCoordinates = true
                    resolutionConfiguration = configurations.runtimeClasspath
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        groupId = "org"
                        artifactId = "runtimeElements"
                        version = "1.0"
                        from comp
                    }
                }
            }
        """
        def repoModule = javaLibrary(mavenRepo.module('org', "runtimeElements", '1.0'))

        when:
        succeeds "publish"

        then:
        // POM uses resolved variant coordinates
        def dependencies = repoModule.parsedPom.scopes.compile.dependencies
        dependencies.size() == 1
        def dependency = dependencies.values().first()
        dependency.groupId == "org.jetbrains.kotlinx"
        dependency.artifactId == "kotlinx-coroutines-core-iosx64"
        dependency.version == "1.7.2"

        // GMM continues to use component coordinates
        def gmmDependencies = repoModule.parsedModuleMetadata.variant("runtimeElements").dependencies
        gmmDependencies.size() == 1
        def gmmDependency = gmmDependencies.first()
        gmmDependency.group == "org.jetbrains.kotlinx"
        gmmDependency.module == "kotlinx-coroutines-core"
        gmmDependency.version == "1.7.2"
    }

    def "publishes resolved child coordinates for multi-coordinate project dependency"() {
        given:
        settingsFile << """
            include 'other'
            rootProject.name = 'root'
        """
        file("other/build.gradle") << """
            plugins {
                id 'maven-publish'
            }
            ${publishMultiCoordinateComponent(otherSeparateConfigurations)}
        """

        buildFile << """
            plugins {
                id 'maven-publish'
            }
            ${publishMultiCoordinateComponent(rootSeparateConfigurations)}

            dependencies {
                firstImplementation project(':other')
                secondImplementation project(':other')
            }
        """

        when:
        [rootSeparateConfigurations, otherSeparateConfigurations].count(true).times {
            executer.expectDocumentedDeprecationWarning("Calling configuration method 'attributes(Action)' is deprecated for configuration 'firstRuntimeElements-published', which has permitted usage(s):\n" +
                "\tDeclarable - this configuration can have dependencies added to it\n" +
                "This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Consumable, Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage")
            executer.expectDocumentedDeprecationWarning("Calling configuration method 'attributes(Action)' is deprecated for configuration 'secondRuntimeElements-published', which has permitted usage(s):\n" +
                "\tDeclarable - this configuration can have dependencies added to it\n" +
                "This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Consumable, Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage")
        }
        succeeds(":publish")

        then:
        def root = javaLibrary(mavenRepo.module('org', 'root', '1.0'))
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def second = javaLibrary(mavenRepo.module('org', 'root-second', '1.0'))

        !root.parsedPom.scopes.compile
        !root.parsedPom.scopes.runtime

        def firstDeps = first.parsedPom.scopes.compile.dependencies
        firstDeps.size() == 1
        def firstDep = firstDeps.values().first()
        firstDep.groupId == "org"
        firstDep.artifactId == "other-first"
        firstDep.version == "1.0"

        def secondDeps = second.parsedPom.scopes.compile.dependencies
        secondDeps.size() == 1
        def secondDep = secondDeps.values().first()
        secondDep.groupId == "org"
        secondDep.artifactId == "other-second"
        secondDep.version == "1.0"

        where:
        rootSeparateConfigurations | otherSeparateConfigurations
        false                      | false
        false                      | true
        true                       | false
        true                       | true
    }

    def "publishes #platform coordinates without any other modifiers"() {
        given:
        resolve("${platform}Implementation project(':other')")

        when:
        succeeds(":publish")

        then:
        def module = javaLibrary(mavenRepo.module('org', "root-${platform}", '1.0'))
        def resolved = module.parsedPom.scopes.compile.dependencies.values()
        resolved*.artifactId == ["other-${platform}"]

        where:
        platform << ["first", "second"]
    }

    def "publishes second coordinates for other using attributes"() {
        given:
        def module = resolve("""
            firstImplementation create(project(':other')) {
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "second"))
                }
            }
        """)

        when:
        succeeds(":publish")

        then:
        def resolved = module.parsedPom.scopes.compile.dependencies.values()
        resolved*.artifactId == ["other-second"]
    }

    def "publishes second coordinates for other using capabilities"() {
        given:
        def module = resolve("""
            firstImplementation create(project(':other')) {
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "third"))
                }
                capabilities {
                    requireCapability("org:other-third:1.0")
                }
            }
        """)

        when:
        succeeds(":publish")

        then:
        def resolved = module.parsedPom.scopes.compile.dependencies.values()
        resolved*.artifactId == ["other-third"]
    }

    def "publishes second coordinates for other using target configuration"() {
        given:
        def module = resolve("firstImplementation project(path: ':other', configuration: 'firstRuntimeElements')")

        when:
        succeeds(":publish")

        then:
        def resolved = module.parsedPom.scopes.compile.dependencies.values()
        resolved*.artifactId == ["other-first"]
    }

    def "publishes second coordinates for two dependencies"() {
        given:
        def module = resolve("""
            firstImplementation create(project(':other')) {
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "second"))
                }
            }
            firstImplementation create(project(':other')) {
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "third"))
                }
                capabilities {
                    requireCapability("org:other-third:1.0")
                }
            }
        """)

        when:
        succeeds(":publish")

        then:
        def resolved = module.parsedPom.scopes.compile.dependencies.values()
        resolved*.artifactId == ["other-second", "other-third"]
    }

    def "cannot publish variant coordinates when publishing two dependencies where one is a targetConfiguration"() {
        given:
        def module = resolve("""
            firstImplementation project(path: ':other', configuration: 'firstRuntimeElements')
            firstImplementation create(project(':other'))
        """)

        when:
        succeeds(":publish")

        then:
        def resolved = module.parsedPom.scopes.compile.dependencies.values()
        resolved*.artifactId == ["other-first", "other-second"]
    }












    def resolve(String declarations) {
        settingsFile << """
            include 'other'
            rootProject.name = 'root'
        """
        file("other/build.gradle") << """
            plugins {
                id 'maven-publish'
            }
            ${publishMultiCoordinateComponent()}
        """

        buildFile << """
            plugins {
                id 'maven-publish'
            }
            ${publishMultiCoordinateComponent()}

            dependencies {
                ${declarations}
            }
        """

        javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
    }

    // Implementing this would require performing artifact resolution while publishing.
    // We should do this eventually.
    @NotYetImplemented
    def "publishes classifier of maven-incompatible dependency"() {
        given:
        settingsFile << """
            include 'other'
            rootProject.name = 'root'
        """
        file("other/build.gradle") << """
            plugins {
                id 'maven-publish'
            }
            ${publishMavenIncompatibleComponent()}
        """

        buildFile << """
            plugins {
                id 'maven-publish'
            }
            ${publishMultiCoordinateComponent()}

            dependencies {
                firstImplementation project(':other')
                secondImplementation project(':other')
            }
        """

        when:
        succeeds(":publish")

        then:
        def root = javaLibrary(mavenRepo.module('org', 'root', '1.0'))
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def second = javaLibrary(mavenRepo.module('org', 'root-second', '1.0'))

        !root.parsedPom.scopes.compile
        !root.parsedPom.scopes.runtime

        def firstDeps = first.parsedPom.scopes.compile.dependencies
        firstDeps.size() == 1
        def firstDep = firstDeps.values().first()
        firstDep.groupId == "org"
        firstDep.artifactId == "other"
        firstDep.version == "1.0"
        firstDep.classifier == "first"

        def secondDeps = second.parsedPom.scopes.compile.dependencies
        secondDeps.size() == 1
        def secondDep = secondDeps.values().first()
        secondDep.groupId == "org"
        secondDep.artifactId == "other"
        secondDep.version == "1.0"
        secondDep.classifier == "second"
    }

    def newCompilation(String name, boolean withSeparatePublishedConfigurations, boolean withOwnAttributes, boolean withOwnCapability = false) {
        """
            configurations {
                dependencyScope("${name}Implementation")
                consumable("${name}RuntimeElements") {
                    extendsFrom(${name}Implementation)
                    ${withOwnAttributes ? """
                        attributes {
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "${name}"))
                        }
                    """ : ""}
                    ${withOwnCapability ? "outgoing.capability('org:other-${name}:1.0')" : "" }
                }
                ${withSeparatePublishedConfigurations ? """
                    create("${name}RuntimeElements-published") {
                        canBeConsumed = false
                        canBeResolved = false
                        extendsFrom(${name}Implementation)
                        ${withOwnAttributes ? """
                            attributes {
                                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "${name}"))
                            }
                        """ : ""}
                        ${withOwnCapability ? "outgoing.capability('org:other-${name}:1.0')" : "" }
                    }
                    """ : ""
                }
                resolvable("${name}RuntimeClasspath") {
                    extendsFrom(${name}Implementation)
                    ${withOwnAttributes ? """
                        attributes {
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "${name}"))
                        }
                    """ : ""}
                }
            }

            def ${name}ChildComponent = factory.adhoc("${name}ChildComponent")
            ${name}ChildComponent.addVariantsFromConfiguration(configurations."${withSeparatePublishedConfigurations ?
                "${name}RuntimeElements-published" : "${name}RuntimeElements"
            }") {
                mapToMavenScope('compile')
                dependencyMapping {
                    publishResolvedCoordinates = true
                    resolutionConfiguration = configurations.${name}RuntimeClasspath
                }
            }

            publishing {
                publications {
                    ${name}Pub(MavenPublication) {
                        groupId = "org"
                        artifactId = project.name + "-${name}"
                        version = "1.0"
                        from ${name}ChildComponent
                    }
                }
            }
        """
    }

    def publishMultiCoordinateComponent(boolean withSeparatePublishedConfigurations = false) {
        """
            abstract class RootComponent implements ComponentWithVariants, org.gradle.api.internal.component.SoftwareComponentInternal {
                private final String name;

                @Inject
                public RootComponent(String name) {
                    this.name = name;
                }

                @Override
                public String getName() {
                    return name;
                }

                @Override
                Set<SoftwareComponentVariant> getUsages() {
                    return Collections.emptySet();
                }

                @Override
                abstract NamedDomainObjectContainer<SoftwareComponent> getVariants();
            }

            def root = project.objects.newInstance(RootComponent, "root")

            interface MyServices {
                @Inject
                SoftwareComponentFactory getSoftwareComponentFactory()
            }
            def factory = objects.newInstance(MyServices).softwareComponentFactory
            ${newCompilation("first", withSeparatePublishedConfigurations, true)}
            ${newCompilation("second", withSeparatePublishedConfigurations, true)}
            ${newCompilation("third", withSeparatePublishedConfigurations, true, true)}

            root.variants.addAll([firstChildComponent, secondChildComponent, thirdChildComponent])

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    rootPub(MavenPublication) {
                        groupId = "org"
                        artifactId = project.name
                        version = "1.0"
                        from root
                    }
                }
            }
        """
    }

    def publishMavenIncompatibleComponent() {
        """
            configurations {
                dependencyScope("firstImplementation")
                consumable("firstElements") {
                    extendsFrom(firstImplementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "first"))
                    }
                    outgoing {
                        artifact(project.file("first.jar")) {
                            classifier = "first"
                        }
                    }
                }
                resolvable("firstRuntimeClasspath") {
                    extendsFrom(firstImplementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "first"))
                    }
                }

                dependencyScope("secondImplementation")
                consumable("secondElements") {
                    extendsFrom(secondImplementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "second"))
                    }
                    outgoing {
                        artifact(project.file("second.jar")) {
                            classifier = "second"
                        }
                    }
                }
                resolvable("secondRuntimeClasspath") {
                    extendsFrom(secondImplementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "second"))
                    }
                }
            }

            interface MyServices {
                @Inject
                SoftwareComponentFactory getSoftwareComponentFactory()
            }
            def factory = objects.newInstance(MyServices).softwareComponentFactory

            def root = factory.adhoc("root")
            root.addVariantsFromConfiguration(configurations.firstElements) {
                mapToMavenScope('compile')
                dependencyMapping {
                    publishResolvedCoordinates = true
                    resolutionConfiguration = configurations.firstRuntimeClasspath
                }
            }

            def second = factory.adhoc("second")
            root.addVariantsFromConfiguration(configurations.secondElements) {
                mapToMavenScope('compile')
                mapToOptional()
                dependencyMapping {
                    publishResolvedCoordinates = true
                    resolutionConfiguration = configurations.secondRuntimeClasspath
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    rootPub(MavenPublication) {
                        groupId = "org"
                        artifactId = project.name
                        version = "1.0"
                        from root
                    }
                }
            }
        """
    }

    def test() {
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()
        buildFile << """
            configurations {
                foo
            }
            dependencies {
                foo "org:bar:1.0"
                constraints {
                    foo "org:foo:1.0"
                }
            }
            task resolve {
                def rootProvider = configurations.foo.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootProvider.get()
                    println root.dependencies.size()
                    println root.dependents.size()
                    println root.selectionReason
                    println root.moduleVersion
                    println root.variants.size()
                    println root.id
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
