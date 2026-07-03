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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests variant reselection. i.e. anything of the form:
 * <pre>
 *     someConf.incoming.artifactView {
 *         withVariantReselection()
 *     }
 * </pre>
 */
class ArtifactVariantReselectionIntegrationTest extends AbstractIntegrationSpec {

    def "variant reselection excludes artifacts for dependency with explicit artifact"() {
        mavenRepo.module("com", "foo").artifact(classifier: "sources").artifact(classifier: "cls").publish()
        buildFile << """
            plugins {
                id("java-library")
            }

            repositories { maven { url = "${mavenRepo.uri}" } }

            dependencies {
                implementation 'com:foo:1.0'
                implementation 'com:foo:1.0:cls'
            }

            task resolve {
                def normal = configurations.runtimeClasspath.incoming.files
                def reselected = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, Category.DOCUMENTATION))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, named(Bundling, Bundling.EXTERNAL))
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, named(DocsType, DocsType.SOURCES))
                    }
                }.files
                doLast {
                    assert normal*.name == ["foo-1.0.jar", "foo-1.0-cls.jar"]

                    // Does not include artifacts from dependency with explicit artifact request.
                    assert reselected*.name == ["foo-1.0-sources.jar"]
                }
            }
        """

        expect:
        succeeds(":resolve")
    }

    def "variant reselection selects among variants with same attributes and different capabilities"() {
        given:
        settingsFile << "include 'producer'"
        file("producer/build.gradle") << multiFeatureProducer()

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":producer")) {
                    capabilities {
                        requireCapability("com.example:producer-${featureName}:1.0")
                    }
                }
            }

            task resolve {
                def normal = configurations.runtimeClasspath.incoming.files
                def reselected = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, Category.DOCUMENTATION))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, named(Bundling, Bundling.EXTERNAL))
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, named(DocsType, DocsType.SOURCES))
                    }
                }.files
                doLast {
                    assert normal*.name == ["producer-1.0-${featureName}.jar"]
                    assert reselected*.name == ${expectedArtifacts}
                }
            }
        """

        expect:
        succeeds(":resolve")

        where:
        featureName      | expectedArtifacts
        // We request sources for a variant that does not have sources.
        // We should not fail, since variant reselection allows no matching variants.
        "without-sources" | "[]"
        // We request sources for a variant that has sources and expect those sources to be reselected.
        "with-sources"    | "[\"producer-1.0-with-sources-sources.jar\"]"
    }

    def "variant reselection selects multiple dependencies from the same project with differing capabilities"() {
        given:
        settingsFile << "include 'producer'"
        file("producer/build.gradle") << multiFeatureProducer()

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":producer")) {
                    capabilities {
                        requireCapability("com.example:producer-without-sources:1.0")
                    }
                }
                implementation(project(":producer")) {
                    capabilities {
                        requireCapability("com.example:producer-with-sources:1.0")
                    }
                }
                implementation(project(":producer")) {
                    capabilities {
                        requireCapability("com.example:producer-with-sources2:1.0")
                    }
                }
            }

            task resolve {
                def normal = configurations.runtimeClasspath.incoming.files
                def reselected = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, Category.DOCUMENTATION))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, named(Bundling, Bundling.EXTERNAL))
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, named(DocsType, DocsType.SOURCES))
                    }
                }.files
                doLast {
                    assert normal*.name == ["producer-1.0-without-sources.jar", "producer-1.0-with-sources.jar", "producer-1.0-with-sources2.jar"]
                    assert reselected*.name == ["producer-1.0-with-sources-sources.jar", "producer-1.0-with-sources2-sources.jar"]
                }
            }
        """

        expect:
        succeeds(":resolve")
    }

    def "withVariantReselection does not follow available-at pointers, since variant reselection picks variants only from components already in the graph"() {
        // 'foo:a:1.0' has an "original" variant with its own file and a "reselected" variant
        // that delegates (via available-at) to 'foo:a-reselected:1.0'.
        // 'foo:b:1.0' has two variants, both with their own files.
        //
        // A configuration whose request attributes already select the "reselected" variant
        // triggers full graph resolution. Graph resolution follows the available-at pointer,
        // adding foo:a-reselected:1.0 to the graph; that component's artifact appears in the
        // result.
        //
        // An ArtifactView built on a configuration that requested the "original" variant and
        // reselects via withVariantReselection() does NOT re-run graph resolution. It picks a
        // different variant of each component already in the graph. The "reselected" variant of
        // foo:a:1.0 is an external variant with no files of its own; since reselection cannot
        // follow the available-at pointer, foo:a-reselected:1.0 never enters the result and the
        // view returns only foo:b:1.0's reselected artifact.
        given:
        mavenRepo.module("foo", "a", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("originalElements", [attr: "original"]) {
                artifact("a-1.0.jar")
            }
            .variant("reselectedElements", [attr: "reselected"]) {
                availableAt("../../a-reselected/1.0/a-reselected-1.0.module", "foo", "a-reselected", "1.0")
            }
            .publish()

        mavenRepo.module("foo", "a-reselected", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("reselectedElements", [attr: "reselected"]) {
                artifact("a-reselected-1.0.jar")
            }
            .publish()

        mavenRepo.module("foo", "b", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("originalElements", [attr: "original"]) {
                artifact("b-1.0.jar")
            }
            .variant("reselectedElements", [attr: "reselected"]) {
                artifact("b-1.0.jar")
            }
            .publish()

        buildFile << """
            def attr = Attribute.of("attr", String)

            repositories { maven { url = "${mavenRepo.uri}" } }

            configurations {
                originalDependency {
                    canBeConsumed = false
                    attributes { attribute(attr, "original") }
                }
                reselectedDependency {
                    canBeConsumed = false
                    attributes { attribute(attr, "reselected") }
                }
            }

            dependencies {
                originalDependency "foo:a:1.0"
                originalDependency "foo:b:1.0"
                reselectedDependency "foo:a:1.0"
                reselectedDependency "foo:b:1.0"
            }

            task resolve {
                def reselectedConfFiles = configurations.reselectedDependency.incoming.files
                def reselectedViewFiles = configurations.originalDependency.incoming.artifactView {
                    withVariantReselection()
                    attributes { attribute(attr, "reselected") }
                }.files
                doLast {
                    // Full graph resolution follows the available-at pointer.
                    assert reselectedConfFiles*.name.toSet() == ["a-reselected-1.0.jar", "b-1.0.jar"].toSet()
                    // Variant reselection does not: the foo:a-reselected:1.0 artifact is absent.
                    assert reselectedViewFiles*.name.toSet() == ["b-1.0.jar"].toSet()
                }
            }
        """

        expect:
        succeeds(":resolve")
    }

    private static String multiFeatureProducer() {
        """
            plugins {
                id 'java-library'
            }

            group = "com.example"
            version = "1.0"

            java {
                withSourcesJar()
            }

            java {
                sourceSets {
                    withoutSources
                }
                registerFeature("withoutSources") {
                    usingSourceSet(sourceSets.withoutSources)
                }
            }

            java {
                sourceSets {
                    withSources
                }
                registerFeature("withSources") {
                    usingSourceSet(sourceSets.withSources)
                    withSourcesJar()
                }
            }

            java {
                sourceSets {
                    withSources2
                }
                registerFeature("withSources2") {
                    usingSourceSet(sourceSets.withSources2)
                    withSourcesJar()
                }
            }
        """
    }
}
