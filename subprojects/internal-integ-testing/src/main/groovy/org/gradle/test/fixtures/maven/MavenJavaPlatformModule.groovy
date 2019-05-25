/*
 * Copyright 2018 the original author or authors.
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

class MavenJavaPlatformModule extends DelegatingMavenModule<MavenFileModule> implements PublishedJavaModule {
    final MavenFileModule mavenModule

    MavenJavaPlatformModule(MavenFileModule mavenModule) {
        super(mavenModule)
        this.mavenModule = mavenModule
    }

    @Override
    PublishedJavaModule withClassifiedArtifact(String classifier, String extension) {
        return this
    }

    @Override
    void assertPublishedAsJavaModule() {
        this.assertPublished()
    }

    @Override
    void assertPublished() {
        super.assertPublished()

        // Verify Gradle metadata particulars
        assert mavenModule.parsedModuleMetadata.variants*.name as Set == ['apiElements', 'runtimeElements'] as Set
        assert mavenModule.parsedModuleMetadata.variant('apiElements').files.empty
        assert mavenModule.parsedModuleMetadata.variant('runtimeElements').files.empty

        // Verify POM particulars
        assert mavenModule.parsedPom.packaging == 'pom'
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
            assert parsedModuleMetadata.variant('runtime').dependencies.empty
            assert parsedPom.scopes.runtime == null
        } else {
            assert parsedModuleMetadata.variant('runtime').dependencies*.coords.containsAll(expected)
            parsedPom.scopes.runtime.assertDependsOn(mavenizeDependencies(expected))
        }
    }

    private static String[] mavenizeDependencies(String[] input) {
        return input.collect {it.replace("latest.integration", "LATEST").replace("latest.release", "RELEASE")}
    }
}
