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
import org.gradle.integtests.fixtures.maven.MavenResolveTestFixture
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

/**
 * Tests behavior of publishing variants that depend on components with multiple coordinates.
 *
 * Particularly,
 * {@link org.gradle.api.publish.internal.mapping.ResolutionBackedVariantDependencyResolver},
 * {@link org.gradle.api.publish.internal.mapping.ResolutionBackedComponentDependencyResolver}, and
 * {@link org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectDependencyPublicationResolver}
 * are exercised.
 */
class MavenPublishExternalVariantIntegrationTest extends AbstractMavenPublishIntegTest implements MavenResolveTestFixture {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "publishes resolved jvm coordinates for multi-coordinates external module dependency"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }

            version = "1.0"

            ${mavenCentralRepository()}
            dependencies {
                implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2"
            }

            components.java.withVariantsFromConfiguration(configurations.runtimeElements) {
                dependencyMapping {
                    publishResolvedCoordinates = true
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        groupId = "org"
                        from components.java
                    }
                }
            }

            ${printClasspathTask()}
        """
        def repoModule = javaLibrary(mavenRepo.module('org', "root", '1.0'))

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

        when:
        def result = mavenResolver.resolveDependency("org", "root", "1.0")

        then:
        result.firstLevelDependencies == ["org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:jar:1.7.2"]

        when:
        succeeds "printRuntimeClasspath"

        then:
        readClasspath() == result.artifactFileNames
    }

    def "publishes resolved non-jvm coordinates for multi-coordinate external module dependency"() {
        given:
        buildFile << """
            ${header()}
            ${multiCoordinateComponent {
                compilation("first") {
                    attributes = """
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "library"))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "kotlin-api"))
                        attribute(Attribute.of("org.jetbrains.kotlin.native.target", String), "ios_x64")
                        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String), "native")
                    """
                }
            }}
            ${mavenCentralRepository()}
            dependencies {
                firstImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2"
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', "root-first", '1.0'))

        // POM uses resolved variant coordinates
        def dependencies = first.parsedPom.scopes.runtime.dependencies
        dependencies.size() == 1
        def dependency = dependencies.values().first()
        dependency.groupId == "org.jetbrains.kotlinx"
        dependency.artifactId == "kotlinx-coroutines-core-iosx64"
        dependency.version == "1.7.2"

        // GMM continues to use component coordinates
        def gmmDependencies = first.parsedModuleMetadata.variant("firstRuntimeElements").dependencies
        gmmDependencies.size() == 1
        def gmmDependency = gmmDependencies.first()
        gmmDependency.group == "org.jetbrains.kotlinx"
        gmmDependency.module == "kotlinx-coroutines-core"
        gmmDependency.version == "1.7.2"

        when:
        mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        def e = thrown(Exception)
        // We fail to publish <type>klib</type>
        e.message.contains("Could not find artifact org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:jar:1.7.2")
    }

    def "preserves artifacts but does not map coordinates when publishing multi-coordinate external module dependencies"() {
        given:
        buildFile << """
            ${header()}
            ${multiCoordinateComponent {
                compilation("first") {
                    attributes = """
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "library"))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "kotlin-api"))
                        attribute(Attribute.of("org.jetbrains.kotlin.native.target", String), "ios_x64")
                        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String), "native")
                    """
                }
            }}
            ${mavenCentralRepository()}
            dependencies {
                firstImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2") {
                    artifact {
                        classifier = "kotlin-tooling-metadata"
                        type = "json"
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', "root-first", '1.0'))

        def firstDependencies = first.parsedPom.scopes.runtime.dependencies
        firstDependencies.size() == 1
        with(firstDependencies.values().first()) {
            groupId == "org.jetbrains.kotlinx"
            artifactId == "kotlinx-coroutines-core"
            version == "1.7.2"
            classifier == "kotlin-tooling-metadata"
            type == "json"
            exclusions.isEmpty()
        }

        when:
        def firstResolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        firstResolution.firstLevelDependencies == ["org.jetbrains.kotlinx:kotlinx-coroutines-core:json:kotlin-tooling-metadata:1.7.2"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        firstResolution.artifactFileNames.contains("kotlinx-coroutines-core-1.7.2-kotlin-tooling-metadata.json")
        // We expect these to be different. Since we are publishing a type/classifier, we the POM depends
        // on different artifacts without also including their transitive dependencies. Instead, maven
        // uses the dependencies of the root component and resolves the JDK variant dependencies instead.
        readClasspath("first") != firstResolution.artifactFileNames
    }

    def "preserves excludes when publishing multi-coordinate external module dependencies"() {
        given:
        buildFile << """
            ${header()}
            ${multiCoordinateComponent {
                compilation("first") {
                    attributes = """
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "library"))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "kotlin-api"))
                        attribute(Attribute.of("org.jetbrains.kotlin.native.target", String), "ios_x64")
                        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String), "native")
                    """
                }
            }}
            ${mavenCentralRepository()}
            dependencies {
                firstImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2") {
                    exclude group: "org.jetbrains.kotlin", module: "kotlin-stdlib-common"
                }
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', "root-first", '1.0'))

        def firstDependencies = first.parsedPom.scopes.runtime.dependencies
        firstDependencies.size() == 1
        with(firstDependencies.values().first()) {
            groupId == "org.jetbrains.kotlinx"
            artifactId == "kotlinx-coroutines-core-iosx64"
            version == "1.7.2"
            classifier == null
            type == null
            exclusions.size() == 1
            def exclusion = exclusions.first()
            exclusion.groupId == "org.jetbrains.kotlin"
            exclusion.artifactId == "kotlin-stdlib-common"
        }

        when:
        mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        def e = thrown(Exception)
        // We fail to publish <type>klib</type>
        e.message.contains("Could not find artifact org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:jar:1.7.2")
    }

    def "publishes resolved child coordinates for multi-coordinate project dependency"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
            compilation("second")
        })

        buildFile << """
            dependencies {
                firstImplementation project(':other')
                secondImplementation project(':other')
            }
        """

        when:
        succeeds "publish"

        then:
        def root = javaLibrary(mavenRepo.module('org', 'root', '1.0'))
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def second = javaLibrary(mavenRepo.module('org', 'root-second', '1.0'))

        !root.parsedPom.scopes.compile
        !root.parsedPom.scopes.runtime

        def firstDeps = first.parsedPom.scopes.runtime.dependencies
        firstDeps.size() == 1
        def firstDep = firstDeps.values().first()
        firstDep.groupId == "org"
        firstDep.artifactId == "other-first"
        firstDep.version == "1.0"

        def secondDeps = second.parsedPom.scopes.runtime.dependencies
        secondDeps.size() == 1
        def secondDep = secondDeps.values().first()
        secondDep.groupId == "org"
        secondDep.artifactId == "other-second"
        secondDep.version == "1.0"

        when:
        def firstResolution = mavenResolver.resolveDependency("org", "root-first", "1.0")
        def secondResolution = mavenResolver.resolveDependency("org", "root-second", "1.0")

        then:
        firstResolution.firstLevelDependencies == ["org:other-first:jar:1.0"]
        secondResolution.firstLevelDependencies == ["org:other-second:jar:1.0"]

        when:
        succeeds ":printFirstRuntimeClasspath", ":printSecondRuntimeClasspath"

        then:
        readClasspath("first") == firstResolution.artifactFileNames
        readClasspath("second") == secondResolution.artifactFileNames
    }

    def "publishes resolved coordinates when using explicit dependency attributes"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
            compilation("second")
        })

        buildFile << """
            dependencies {
                firstImplementation create(project(':other')) {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "second"))
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["other-second"]

        when:
        def resolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        resolution.firstLevelDependencies == ["org:other-second:jar:1.0"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        readClasspath("first") == resolution.artifactFileNames
    }

    def "publishes resolved coordinates when using explicit dependency capabilities"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
            compilation("second") {
                attributes = "attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'second'))"
                capabilities = "capability('org:other-second:1.0')"
            }
        })

        buildFile << """
            dependencies {
                firstImplementation create(project(':other')) {
                    capabilities {
                        requireCapability("org:other-second:1.0")
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["other-second"]

        when:
        def resolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        resolution.firstLevelDependencies == ["org:other-second:jar:1.0"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        readClasspath("first") == resolution.artifactFileNames
    }

    def "publishes resolved coordinates when using targetConfiguration"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
            compilation("second") {
                attributes = "attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'second'))"
            }
        })

        buildFile << """
            dependencies {
                firstImplementation project(path: ':other', configuration: 'secondRuntimeElements')
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["other-second"]

        when:
        def resolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        resolution.firstLevelDependencies == ["org:other-second:jar:1.0"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        readClasspath("first") == resolution.artifactFileNames
    }

    def "publishes coordinates for multiple dependencies"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
            compilation("second") {
                attributes = "attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'second'))"
                capabilities = "capability('org:other-second:1.0')"
            }
            compilation("third") {
                attributes = "attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, 'first'))"
                capabilities = "capability('org:other-third:1.0')"
            }
        })

        buildFile << """
            dependencies {
                firstImplementation create(project(':other'))
                firstImplementation create(project(':other')) {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "second"))
                    }
                    capabilities {
                        requireCapability("org:other-second:1.0")
                    }
                }
                firstImplementation create(project(':other')) {
                    capabilities {
                        requireCapability("org:other-third:1.0")
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["other-first", "other-second", "other-third"]

        when:
        def resolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        resolution.firstLevelDependencies == ["org:other-first:jar:1.0", "org:other-second:jar:1.0", "org:other-third:jar:1.0"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        readClasspath("first") == resolution.artifactFileNames
    }

    def "publishes resolved coordinates when two dependencies map to the same coordinates"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
        })
        file("other/build.gradle") << """
            configurations {
                consumable("extraRuntimeElements") {
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "extra"))
                    }
                }
            }

            firstChildComponent.addVariantsFromConfiguration(configurations.extraRuntimeElements) {
                mapToMavenScope('runtime')
            }
        """

        buildFile << """
            dependencies {
                firstImplementation create(project(':other'))
                firstImplementation project(path: ':other', configuration: 'extraRuntimeElements')
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["other-first"]

        when:
        def resolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        resolution.firstLevelDependencies == ["org:other-first:jar:1.0"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        readClasspath("first") == resolution.artifactFileNames
    }

    def "preserves excludes when publishing multi-coordinate project dependencies"() {
        given:
        mavenRepo.module("com", "example", "2.0").publish()
        publishes(multiCoordinateComponent {
            compilation("first")
            compilation("second")
        })

        file("other/build.gradle") << """
            repositories { maven { url "${mavenRepo.uri}" } }
            dependencies {
                firstImplementation "com:example:2.0"
            }
        """

        buildFile << """
            dependencies {
                firstImplementation create(project(':other')) {
                    exclude group: "com", module: "example"
                }
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', "root-first", '1.0'))
        def dependencies = first.parsedPom.scopes.runtime.dependencies
        dependencies.size() == 1
        with(dependencies.values().first()) {
            groupId == "org"
            artifactId == "other-first"
            version == "1.0"
            classifier == null
            type == null
            exclusions.size() == 1
            def exclusion = exclusions.first()
            exclusion.groupId == "com"
            exclusion.artifactId == "example"
        }

        when:
        def resolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        resolution.firstLevelDependencies == ["org:other-first:jar:1.0"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        readClasspath("first") == resolution.artifactFileNames
    }

    def "resolves single-coordinate components"() {
        given:
        mavenRepo.module("org", "foo", "3.0").publish()
        settingsFile << """
            include 'other'
        """
        file("other/build.gradle") << """
            ${header()}

            file("other-2.0.jar").createNewFile()

            configurations {
                consumable("elements") {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "first"))
                    }
                    outgoing {
                        artifact(file("other-2.0.jar"))
                    }
                }
            }

            def component = componentFactory.adhoc("component")
            component.addVariantsFromConfiguration(configurations.elements) {
                mapToMavenScope('runtime')
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        groupId = "com"
                        artifactId = project.name
                        version = "2.0"
                        from component
                    }
                }
            }
        """
        buildFile << """
            ${header()}
            ${multiCoordinateComponent {
                compilation("first")
            }}
            repositories { maven { url "${mavenRepo.uri}" } }
            dependencies {
                firstImplementation project(":other")
                firstImplementation "org:foo:3.0"
            }
        """

        when:
        succeeds "publish"

        then:
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.groupId == ["com", "org"]
        resolved*.artifactId == ["other", "foo"]
        resolved*.version == ["2.0", "3.0"]

        when:
        def resolution = mavenResolver.resolveDependency("org", "root-first", "1.0")

        then:
        resolution.firstLevelDependencies == ["com:other:jar:2.0", "org:foo:jar:3.0"]

        when:
        succeeds ":printFirstRuntimeClasspath"

        then:
        readClasspath("first") == resolution.artifactFileNames
    }

    def "warns when when two dependencies are ambiguous"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
            compilation("second") {
                attributes = "attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'second'))"
            }
        })

        buildFile << """
            dependencies {
                firstImplementation create(project(':other'))
                firstImplementation project(path: ':other', configuration: 'secondRuntimeElements')
            }
        """

        when:
        succeeds "publish"

        then:
        outputContains("""Maven publication 'firstPub' pom metadata warnings (silence with 'suppressPomMetadataWarningsFor(variant)'):
  - Variant firstRuntimeElements:
      -  contains dependencies that will produce a pom file that cannot be consumed by a Maven client.
          - Cannot determine variant coordinates for Project dependency ':other' since multiple dependencies ambiguously map to different resolved coordinates.""")

        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["other"]
    }

    def "warns when declared project dependency is not in resolution configuration"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
        })

        buildFile << """
            configurations {
                resolvable("noDeps")
            }

            firstChildComponent.withVariantsFromConfiguration(configurations.firstRuntimeElements) {
                dependencyMapping {
                    fromResolutionOf(configurations.noDeps)
                }
            }


            dependencies {
                firstImplementation project(':other')
            }
        """

        when:
        succeeds "publish"

        then:
        outputContains("""Maven publication 'firstPub' pom metadata warnings (silence with 'suppressPomMetadataWarningsFor(variant)'):
  - Variant firstRuntimeElements:
      -  contains dependencies that will produce a pom file that cannot be consumed by a Maven client.
          - Cannot determine variant coordinates for Project dependency ':other' since the resolved graph does not contain the requested project.""")

        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["other"]
    }

    def "warns when declared module dependency is not in resolution configuration"() {
        given:
        mavenRepo.module("org", "foo").publish()
        publishes(multiCoordinateComponent {
            compilation("first")
        })

        buildFile << """
            configurations {
                resolvable("noDeps")
            }

            firstChildComponent.withVariantsFromConfiguration(configurations.firstRuntimeElements) {
                dependencyMapping {
                    fromResolutionOf(configurations.noDeps)
                }
            }

            repositories { maven { url "${mavenRepo.uri}" } }
            dependencies {
                firstImplementation "org:foo:1.0"
            }
        """

        when:
        succeeds "publish"

        then:
        outputContains("""Maven publication 'firstPub' pom metadata warnings (silence with 'suppressPomMetadataWarningsFor(variant)'):
  - Variant firstRuntimeElements:
      -  contains dependencies that will produce a pom file that cannot be consumed by a Maven client.
          - Cannot determine variant coordinates for dependency 'org:foo' since the resolved graph does not contain the requested module.""")

        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def resolved = first.parsedPom.scopes.runtime.dependencies.values()
        resolved*.artifactId == ["foo"]
    }

    def "fails if resolution fails"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first")
        })

        buildFile << """
            repositories { maven { url "${mavenRepo.uri}" } }
            dependencies {
                firstImplementation "org:foo:1.0"
            }
        """

        when:
        fails("publish")

        then:
        failure.assertHasCause("Could not find org:foo:1.0")

        when:
        fails("publish", "--stacktrace")

        then:
        // Not sure why this does not show up without stacktrace
        failure.assertHasErrorOutput("Could not map coordinates for org:foo:1.0")
    }

    // This simulates the way AGP and KGP publish
    @NotYetImplemented
    def "publishes resolved child coordinates for multi-coordinate project dependency when target component uses separate local and published configurations"() {
        given:
        publishes(multiCoordinateComponent {
            compilation("first") {
                withSeparatePublishedConfiguration = true
            }
            compilation("second") {
                withSeparatePublishedConfiguration = true
            }
        })

        buildFile << """
            dependencies {
                firstImplementation project(':other')
                secondImplementation project(':other')
            }
        """

        when:
        succeeds "publish"

        then:
        def root = javaLibrary(mavenRepo.module('org', 'root', '1.0'))
        def first = javaLibrary(mavenRepo.module('org', 'root-first', '1.0'))
        def second = javaLibrary(mavenRepo.module('org', 'root-second', '1.0'))

        !root.parsedPom.scopes.compile
        !root.parsedPom.scopes.runtime

        def firstDeps = first.parsedPom.scopes.runtime.dependencies
        firstDeps.size() == 1
        def firstDep = firstDeps.values().first()
        firstDep.groupId == "org"
        firstDep.artifactId == "other-first"
        firstDep.version == "1.0"

        def secondDeps = second.parsedPom.scopes.runtime.dependencies
        secondDeps.size() == 1
        def secondDep = secondDeps.values().first()
        secondDep.groupId == "org"
        secondDep.artifactId == "other-second"
        secondDep.version == "1.0"
    }

    // region Component builder fixture

    // This test fixture facilitates the creation of custom components that
    // span over multiple coordinates by publishing artifacts from multiple
    // compilations. We may eventually pull this out into a separate file
    // once its functionality stabilizes

    class CompilationDetails {
        boolean withSeparatePublishedConfiguration = false
        String attributes
        String capabilities = ""
        String outputFile
    }

    class RootComponentDetails {
        Map<String, CompilationDetails> compilations = [:]
        void compilation(String name, @DelegatesTo(CompilationDetails) Closure<?> spec = {}) {
            def compilationDetails = new CompilationDetails()
            compilationDetails.attributes = """
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "${name}"))
            """
            compilationDetails.outputFile = "\${project.name}-${name}-1.0.jar"

            spec.delegate = compilationDetails
            spec.call(compilationDetails)
            compilations[name] = compilationDetails
        }
    }

    def publishes(String component) {
        settingsFile << """
            include 'other'
        """

        def content = """
            ${header()}
            ${component}
        """

        file("other/build.gradle") << content
        buildFile << content
    }

    /**
     * Create a build file that publishes a component as described by {@code spec}.
     */
    def multiCoordinateComponent(@DelegatesTo(RootComponentDetails) Closure<?> spec) {

        def details = new RootComponentDetails()
        spec.delegate = details
        spec.call(details)

        """
            def root = project.objects.newInstance(RootComponent, "root")

            ${details.compilations.entrySet().collect { newCompilation(it.getKey(), it.getValue(), "root")}.join("\n")}

            publishing {
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

    /**
     * Buildscript code that should only be defined once
     */
    def header() {
        """
            plugins {
                id 'maven-publish'
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            // Need to implement SoftwareComponentInternal since publishing assumes all components are internal.
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

            // This is the only public API way to obtain a SoftwareComponentFactory.
            interface PublishServices {
                @Inject
                SoftwareComponentFactory getSoftwareComponentFactory()
            }
            def componentFactory = objects.newInstance(PublishServices).softwareComponentFactory
        """
    }

    def newCompilation(String name, CompilationDetails details, String rootComponentName) {
        def runtimeClasspath = "${name}RuntimeClasspath"
        def implementation = "${name}Implementation"
        def runtimeElements = "${name}RuntimeElements"

        def output = """
            file("${details.outputFile}").createNewFile()

            configurations {
                dependencyScope("${implementation}")
                consumable("${runtimeElements}") {
                    extendsFrom(${implementation})
                    attributes {
                        ${details.attributes}
                    }
                    outgoing {
                        ${details.capabilities}
                        artifact(file("${details.outputFile}"))
                    }
                }
                resolvable("${runtimeClasspath}") {
                    extendsFrom(${implementation})
                    attributes {
                        ${details.attributes}
                    }
                }
            }

            ${printClasspathTask(name)}
        """

        def publicationConf = runtimeElements
        if (details.withSeparatePublishedConfiguration) {
            publicationConf = "${runtimeElements}-published"
            output += """
                configurations {
                    create("${publicationConf}") {
                        canBeConsumed = false
                        canBeResolved = false
                        extendsFrom(${implementation})
                        attributes {
                            ${details.attributes}
                        }
                        outgoing {
                            ${details.capabilities}
                        }
                    }
                }
            """
        }

        def component = "${name}ChildComponent"
        output += """
            def ${component} = componentFactory.adhoc("${component}")
            ${component}.addVariantsFromConfiguration(configurations."${publicationConf}") {
                mapToMavenScope('runtime')
                dependencyMapping {
                    publishResolvedCoordinates = true
                    fromResolutionOf(configurations.${runtimeClasspath})
                }
            }

            publishing {
                publications {
                    ${name}Pub(MavenPublication) {
                        groupId = "org"
                        artifactId = project.name + "-${name}"
                        version = "1.0"
                        from ${component}
                    }
                }
            }

            ${rootComponentName}.variants.add(${component})
        """

        output
    }

    /**
     * Prints the classpath for a compilation with the given name, including the compilation's own artifacts.
     * {@link #readClasspath(String)} will read the output of this task into a set, which should match the
     * classpath resolved by Maven.
     */
    def printClasspathTask(String name = "") {
        String runtimeClasspath = name.isEmpty() ? "runtimeClasspath" : "${name}RuntimeClasspath"
        String runtimeElements = name.isEmpty() ? "runtimeElements" : "${name}RuntimeElements"

        """
            task print${name.capitalize()}RuntimeClasspath {
                def artifacts = configurations.${runtimeElements}.outgoing.artifacts.files
                def classpath = configurations.${runtimeClasspath}.incoming.files
                def outputFile = layout.buildDir.file("${name}-classpath.txt")
                outputs.file(outputFile)
                doLast {
                    def allFiles = classpath.files + artifacts.files
                    outputFile.get().getAsFile().text = allFiles*.name.join("\\n")
                }
            }
        """
    }

    Set<String> readClasspath(String name = "") {
        file("build/${name}-classpath.txt").readLines() as Set
    }

    // endregion
}
