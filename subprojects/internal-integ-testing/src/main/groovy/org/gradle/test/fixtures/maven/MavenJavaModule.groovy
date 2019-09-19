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
import org.gradle.util.GUtil

class MavenJavaModule extends DelegatingMavenModule<MavenFileModule> implements PublishedJavaModule {
    final MavenFileModule mavenModule
    final boolean withDocumentation
    private final List<String> additionalArtifacts = []

    MavenJavaModule(MavenFileModule mavenModule, boolean withDocumentation = false) {
        super(mavenModule)
        this.mavenModule = mavenModule
        this.mavenModule.attributes[TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name] = JavaVersion.current().majorVersion
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

    void assertPublished(String pomPackaging, String artifactFileExtension) {
        super.assertPublished()
        assertArtifactsPublished(artifactFileExtension)

        // Verify Gradle metadata particulars
        def expectedVariants = ['apiElements', 'runtimeElements']
        if (withDocumentation) {
            expectedVariants.addAll(['apiElementsJavadoc', 'runtimeElementsSources'])
        }
        assert mavenModule.parsedModuleMetadata.variants*.name as Set == expectedVariants as Set
        def apiElements = mavenModule.parsedModuleMetadata.variant('apiElements')
        def runtimeElements = mavenModule.parsedModuleMetadata.variant('runtimeElements')

        assert apiElements.files*.name == [artifact(artifactFileExtension)]
        assert runtimeElements.files*.name == [artifact(artifactFileExtension)]

        // Verify it contains expected attributes
        def currentJavaVersion = JavaVersion.current().majorVersion.toInteger()
        assertAttributes(apiElements, ["org.gradle.category": "library",
                                       "org.gradle.dependency.bundling": "external",
                                       "org.gradle.jvm.version": currentJavaVersion,
                                       "org.gradle.libraryelements": "jar",
                                       "org.gradle.usage": "java-api"])
        assertAttributes(runtimeElements, ["org.gradle.category": "library",
                                           "org.gradle.dependency.bundling": "external",
                                           "org.gradle.jvm.version": currentJavaVersion,
                                           "org.gradle.libraryelements": "jar",
                                           "org.gradle.usage": "java-runtime"])

        if (withDocumentation) {
            assertDocumentationVariantsPublished()
        }

        // Verify POM particulars
        assert mavenModule.parsedPom.packaging == pomPackaging
    }

    void assertArtifactsPublished(String artifactFileExtension = "jar") {
        List<String> expectedArtifacts = [artifact("module"), artifact("pom"), artifact(artifactFileExtension)]
        if (withDocumentation) {
            expectedArtifacts.addAll([artifact("javadoc", "jar"), artifact("sources", "jar")])
        }
        expectedArtifacts.addAll(additionalArtifacts)
        mavenModule.assertArtifactsPublished(expectedArtifacts)
    }

    private void assertDocumentationVariantsPublished() {
        def javadoc = mavenModule.parsedModuleMetadata.variant('apiElementsJavadoc')
        def sources = mavenModule.parsedModuleMetadata.variant('runtimeElementsSources')

        assert javadoc.files*.name == [artifact("javadoc", "jar")]
        assert sources.files*.name == [artifact("sources", "jar")]

        def currentJavaVersion = JavaVersion.current().majorVersion.toInteger()
        assertAttributes(javadoc, ["org.gradle.category": "documentation",
                                   "org.gradle.dependency.bundling": "external",
                                   "org.gradle.docstype": "javadoc",
                                   "org.gradle.jvm.version": currentJavaVersion,
                                   "org.gradle.libraryelements": "jar",
                                   "org.gradle.usage": "java-api"])
        assertAttributes(sources, ["org.gradle.category": "documentation",
                                   "org.gradle.dependency.bundling": "external",
                                   "org.gradle.docstype": "sources",
                                   "org.gradle.jvm.version": currentJavaVersion,
                                   "org.gradle.libraryelements": "jar",
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
        assertDependencies('apiElements', 'compile', [], expected)
        assert parsedModuleMetadata.variant('runtimeElements').dependencies*.coords.containsAll(expected)
        if (withDocumentation) {
            assertDependencies('apiElementsJavadoc', 'compile', [], expected)
            assert parsedModuleMetadata.variant('runtimeElementsSources').dependencies*.coords.containsAll(expected)
        }
    }

    @Override
    void assertRuntimeDependencies(String... expected) {
        def apiDependencies = parsedModuleMetadata.variant('apiElements').dependencies
        assertDependencies('runtimeElements', 'runtime', apiDependencies, expected)
        if (withDocumentation) {
            assertDependencies('runtimeElementsSources', 'runtime', apiDependencies, expected)
        }
    }

    private void assertDependencies(String variant, String mavenScope, List<GradleModuleMetadata.Dependency> additionalGMMDependencies, String... expected) {
        if (expected.length == 0) {
            assert parsedModuleMetadata.variant(variant).dependencies*.coords as Set == additionalGMMDependencies*.coords as Set
            assert parsedPom.scopes."$mavenScope" == null
        } else {
            assert parsedModuleMetadata.variant(variant).dependencies*.coords as Set == (additionalGMMDependencies*.coords as Set) + (expected as Set)
            parsedPom.scopes."$mavenScope".assertDependsOn(mavenizeDependencies(expected))
        }
    }

    private String artifact(String classifier = null, String extension) {
        def artifactName = "${artifactId}-${publishArtifactVersion}"
        if (GUtil.isTrue(classifier)) {
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
