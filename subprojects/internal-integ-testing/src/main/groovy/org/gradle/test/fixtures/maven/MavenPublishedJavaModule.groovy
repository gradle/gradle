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

class MavenPublishedJavaModule implements PublishedJavaModule {
    private final MavenModule module

    MavenPublishedJavaModule(MavenModule module) {
        this.module = module
    }

    @Override
    void assertPublished() {
        String moduleVersion = "${module.artifactId}-${module.publishArtifactVersion}"
        module.assertPublished()
        module.assertArtifactsPublished("${moduleVersion}.module", "${moduleVersion}.pom", "${moduleVersion}.jar")

        // Verify Gradle metadata particulars
        assert module.parsedModuleMetadata.variants*.name as Set == ['api', 'runtime'] as Set
        assert module.parsedModuleMetadata.variant('api').files*.name == [moduleVersion + ".jar"]
        assert module.parsedModuleMetadata.variant('runtime').files*.name == [moduleVersion + ".jar"]

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
             assert module.parsedPom.scopes.compile == null
             assert module.parsedModuleMetadata.variant('api').dependencies.empty
        } else {
            module.parsedPom.scopes.compile.assertDependsOn(expected)
            assert module.parsedModuleMetadata.variant('api').dependencies*.coords as Set == expected as Set
            assert module.parsedModuleMetadata.variant('runtime').dependencies*.coords.containsAll(expected)
        }
    }

    @Override
    void assertRuntimeDependencies(String... expected) {
        if (expected.length == 0) {
            assert module.parsedPom.scopes.runtime == null
            assert module.parsedModuleMetadata.variant('runtime').dependencies.empty
        } else {
            module.parsedPom.scopes.runtime.assertDependsOn(expected)
            assert module.parsedModuleMetadata.variant('runtime').dependencies*.coords.containsAll(expected)
        }
    }
}
