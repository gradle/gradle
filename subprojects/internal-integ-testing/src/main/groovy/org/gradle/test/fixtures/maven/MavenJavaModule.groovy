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

import org.gradle.test.fixtures.PublishedJavaModule
import org.gradle.util.GUtil

class MavenJavaModule extends DelegatingMavenModule<MavenFileModule> implements PublishedJavaModule {
    private final MavenFileModule module
    private final List<String> additionalArtifacts = []

    MavenJavaModule(MavenFileModule module) {
        super(module)
        this.module = module
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
        module.assertArtifactsPublished(expectedArtifacts as String[])

        // Verify Gradle metadata particulars
        assert module.parsedModuleMetadata.variants*.name as Set == ['api', 'runtime'] as Set
        assert module.parsedModuleMetadata.variant('api').files*.name == [artifact('jar')]
        assert module.parsedModuleMetadata.variant('runtime').files*.name == [artifact('jar')]

        // Verify POM particulars
        assert module.parsedPom.packaging == null
    }

    void assertNoDependencies() {
        assert module.parsedPom.scopes.isEmpty()
        assertApiDependencies()
    }


    @Override
    void assertApiDependencies(String... expected) {
        if (expected.length == 0) {
            assert parsedModuleMetadata.variant('api').dependencies.empty
            assert parsedPom.scopes.compile == null
        } else {
            assert parsedModuleMetadata.variant('api').dependencies*.coords as Set == expected as Set
            assert parsedModuleMetadata.variant('runtime').dependencies*.coords.containsAll(expected)
            parsedPom.scopes.compile.assertDependsOn(mavenizeDependencies(expected))
        }
    }

    @Override
    void assertRuntimeDependencies(String... expected) {
        if (expected.length == 0) {
            assert parsedModuleMetadata.variant('runtime').dependencies.empty
            assert parsedPom.scopes.runtime == null
        } else {
            assert parsedModuleMetadata.variant('runtime').dependencies*.coords.containsAll(expected)
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
