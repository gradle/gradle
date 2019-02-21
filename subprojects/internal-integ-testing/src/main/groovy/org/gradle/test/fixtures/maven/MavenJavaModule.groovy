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
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.test.fixtures.PublishedJavaModule
import org.gradle.util.GUtil

class MavenJavaModule extends DelegatingMavenModule<MavenFileModule> implements PublishedJavaModule {
    final MavenFileModule mavenModule
    private final List<String> additionalArtifacts = []

    MavenJavaModule(MavenFileModule mavenModule) {
        super(mavenModule)
        this.mavenModule = mavenModule
        this.mavenModule.attributes[TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name] = JavaVersion.current().majorVersion
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
        super.assertPublished()

        List<String> expectedArtifacts = [artifact("module"), artifact("pom"), artifact("jar")]
        expectedArtifacts.addAll(additionalArtifacts)
        mavenModule.assertArtifactsPublished(expectedArtifacts)

        // Verify Gradle metadata particulars
        assert mavenModule.parsedModuleMetadata.variants*.name as Set == ['apiElements', 'runtimeElements'] as Set
        def apiElements = mavenModule.parsedModuleMetadata.variant('apiElements')
        def runtimeElements = mavenModule.parsedModuleMetadata.variant('runtimeElements')

        assert apiElements.files*.name == [artifact('jar')]
        assert runtimeElements.files*.name == [artifact('jar')]

        // Verify it contains some expected attributes
        assert apiElements.attributes.containsKey(Bundling.BUNDLING_ATTRIBUTE.name)
        assert apiElements.attributes.containsKey(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name)
        assert runtimeElements.attributes.containsKey(Bundling.BUNDLING_ATTRIBUTE.name)
        assert runtimeElements.attributes.containsKey(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name)

        // Verify POM particulars
        assert mavenModule.parsedPom.packaging == null
    }

    void assertNoDependencies() {
        assert mavenModule.parsedPom.scopes.isEmpty()
        assertApiDependencies()
    }


    @Override
    void assertApiDependencies(String... expected) {
        if (expected.length == 0) {
            assert parsedModuleMetadata.variant('apiElements').dependencies.empty
            assert parsedPom.scopes.compile == null
        } else {
            assert parsedModuleMetadata.variant('apiElements').dependencies*.coords as Set == expected as Set
            assert parsedModuleMetadata.variant('runtimeElements').dependencies*.coords.containsAll(expected)
            parsedPom.scopes.compile.assertDependsOn(mavenizeDependencies(expected))
        }
    }

    @Override
    void assertRuntimeDependencies(String... expected) {
        if (expected.length == 0) {
            assert parsedModuleMetadata.variant('runtimeElements').dependencies.empty
            assert parsedPom.scopes.runtime == null
        } else {
            assert parsedModuleMetadata.variant('runtimeElements').dependencies*.coords.containsAll(expected)
            parsedPom.scopes.runtime.assertDependsOn(mavenizeDependencies(expected))
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
