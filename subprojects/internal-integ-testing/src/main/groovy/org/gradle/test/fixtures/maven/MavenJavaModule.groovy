/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.test.fixtures.maven

import org.gradle.api.JavaVersion
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.test.fixtures.GradleModuleMetadata
import org.gradle.test.fixtures.PublishedJavaModule
import org.gradle.util.internal.GUtil

class MavenJavaModule extends DelegatingMavenModule<MavenFileModule> implements PublishedJavaModule {
    final static String MAIN_FEATURE = 'main'

    final MavenFileModule mavenModule
    final boolean withDocumentation
    final List<String> features
    private final List<String> additionalArtifacts = []

    MavenJavaModule(MavenFileModule mavenModule, List<String> features, boolean withDocumentation) {
        super(mavenModule)
        this.mavenModule = mavenModule
        this.mavenModule.attributes[TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name] = JavaVersion.current().majorVersion
        this.features = features
        this.withDocumentation = withDocumentation
    }

    @Override
    PublishedJavaModule withClassifiedArtifact(String classifier, String extension) {
        additionalArtifacts << artifact(classifier, extension)
        return this
    }

    @Override
    void assertPublishedAsJavaModule() {
        this.assertPublished()
    }

    @Override
    void assertPublished() {
        assertPublished(null, "jar")
    }

    static String variantName(String featureName, String baseName) {
        if (featureName == MAIN_FEATURE) {
            baseName
        } else {
            featureName + 'SourceSet' + baseName.capitalize()
        }
    }

    private static String featurePrefix(String feature) {
        feature == MAIN_FEATURE ? "" : "$feature-"
    }

    void assertPublished(String pomPackaging, String artifactFileExtension) {
        super.assertPublished()
        assertArtifactsPublished(artifactFileExtension)

        // Verify Gradle metadata particulars
        def expectedVariants = [] as TreeSet
        features.each { feature ->
            expectedVariants.addAll([variantName(feature, 'apiElements'), variantName(feature, 'runtimeElements')])
            if (withDocumentation) {
                expectedVariants.addAll([variantName(feature, 'javadocElements'), variantName(feature, 'sourcesElements')])
            }
        }
        def parsedModuleVariants = mavenModule.parsedModuleMetadata.variants*.name as TreeSet
        assert parsedModuleVariants == expectedVariants

        features.each { feature ->
            assertMainVariantsPublished(feature, artifactFileExtension)
            if (withDocumentation) {
                assertDocumentationVariantsPublished(feature)
            }
        }

        // Verify POM particulars
        assert mavenModule.parsedPom.packaging == pomPackaging
    }

    private void assertMainVariantsPublished(String feature, String artifactFileExtension) {
        def apiElements = mavenModule.parsedModuleMetadata.variant(variantName(feature, 'apiElements'))
        def runtimeElements = mavenModule.parsedModuleMetadata.variant(variantName(feature, 'runtimeElements'))

        assert apiElements.files*.name == [artifact(feature, artifactFileExtension)]
        assert runtimeElements.files*.name == [artifact(feature, artifactFileExtension)]

        // Verify it contains expected attributes
        def currentJavaVersion = JavaVersion.current().majorVersion.toInteger()
        assertAttributes(apiElements, ["org.gradle.category": "library",
                                       "org.gradle.dependency.bundling": "external",
                                       "org.gradle.jvm.version": currentJavaVersion,
                                       "org.gradle.libraryelements": "jar",
                                       "org.gradle.usage": "java-api",
                                      ])
        assertAttributes(runtimeElements, ["org.gradle.category": "library",
                                           "org.gradle.dependency.bundling": "external",
                                           "org.gradle.jvm.version": currentJavaVersion,
                                           "org.gradle.libraryelements": "jar",
                                           "org.gradle.usage": "java-runtime"])
    }

    void assertArtifactsPublished(String artifactFileExtension = "jar") {
        List<String> expectedArtifacts = [artifact("module"), artifact("pom")]
        features.each { feature ->
            expectedArtifacts.add(artifact(feature, artifactFileExtension))
            if (withDocumentation) {
                expectedArtifacts.addAll([artifact("${featurePrefix(feature)}javadoc", "jar"), artifact("${featurePrefix(feature)}sources", "jar")])
            }
        }
        expectedArtifacts.addAll(additionalArtifacts)
        mavenModule.assertArtifactsPublished(expectedArtifacts)
    }

    private void assertDocumentationVariantsPublished(String feature) {
        def javadoc = mavenModule.parsedModuleMetadata.variant(variantName(feature, 'javadocElements'))
        def sources = mavenModule.parsedModuleMetadata.variant(variantName(feature, 'sourcesElements'))

        assert javadoc.files*.name == [artifact("${featurePrefix(feature)}javadoc", "jar")]
        assert sources.files*.name == [artifact("${featurePrefix(feature)}sources", "jar")]

        assertAttributes(javadoc, ["org.gradle.category": "documentation",
                                   "org.gradle.dependency.bundling": "external",
                                   "org.gradle.docstype": "javadoc",
                                   "org.gradle.usage": "java-runtime"])
        assertAttributes(sources, ["org.gradle.category": "documentation",
                                   "org.gradle.dependency.bundling": "external",
                                   "org.gradle.docstype": "sources",
                                   "org.gradle.usage": "java-runtime"])
    }

    private static void assertAttributes(GradleModuleMetadata.Variant variant, Map<String, Object> expected) {
        assert variant.attributes.size() == expected.size()
        expected.each { attribute, value ->
            assert variant.attributes.containsKey(attribute)
            assert variant.attributes[attribute] == value
        }
    }

    void assertNoDependencies() {
        assert mavenModule.parsedPom.scopes.isEmpty()
        assertApiDependencies()
    }

    @Override
    void assertApiDependencies(String... expected) {
        features.each { feature ->
            assertDependencies(feature, 'apiElements', 'compile', [], expected)
            assert parsedModuleMetadata.variant('runtimeElements').dependencies*.coords.containsAll(expected)
        }
    }

    @Override
    void assertRuntimeDependencies(String... expected) {
        features.each { feature ->
            def apiDependencies = feature == MAIN_FEATURE ? parsedModuleMetadata.variant('apiElements').dependencies : []
            assertDependencies(feature, 'runtimeElements', 'runtime', apiDependencies, expected)
            if (withDocumentation) {
                // documentation never has dependencies by default
                assert parsedModuleMetadata.variant(variantName(feature, 'javadocElements')).dependencies.empty
                assert parsedModuleMetadata.variant(variantName(feature, 'sourcesElements')).dependencies.empty
            }
        }
    }

    void assertDependencies(String feature, String variant, String mavenScope, List<GradleModuleMetadata.Dependency> additionalGMMDependencies, String... expected) {
        if (feature != MAIN_FEATURE) {
            // no dependencies for optional features in this test
            assert parsedModuleMetadata.variant(variantName(feature, variant)).dependencies.empty
        } else if (expected.size() == 0) {
            assert parsedModuleMetadata.variant(variantName(feature, variant)).dependencies*.coords as Set == additionalGMMDependencies*.coords as Set
            assert parsedPom.scopes."$mavenScope" == null
        } else {
            assert parsedModuleMetadata.variant(variantName(feature, variant)).dependencies*.coords as Set == (additionalGMMDependencies*.coords as Set) + (expected as Set)
            parsedPom.scopes."$mavenScope".assertDependsOn(mavenizeDependencies(expected))
        }
    }

    private String artifact(String classifier = null, String extension) {
        def artifactName = "${artifactId}-${publishArtifactVersion}"
        if (GUtil.isTrue(classifier) && classifier != MAIN_FEATURE) {
            artifactName += "-${classifier}"
        }
        if (GUtil.isTrue(extension)) {
            artifactName += ".${extension}"
        }
        return artifactName.toString()
    }

    private static String[] mavenizeDependencies(String[] input) {
        return input.collect {it.replace("latest.integration", "LATEST").replace("latest.release", "RELEASE")}
    }
}
