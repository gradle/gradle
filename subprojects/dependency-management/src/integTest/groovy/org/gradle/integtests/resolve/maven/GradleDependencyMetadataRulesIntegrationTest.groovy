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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.ExperimentalFeaturesFixture
import org.gradle.integtests.resolve.DependencyMetadataRulesIntegrationTest
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class GradleDependencyMetadataRulesIntegrationTest extends DependencyMetadataRulesIntegrationTest {

    def setup() {
        ExperimentalFeaturesFixture.enable(settingsFile)
    }

    @Override
    MavenHttpRepository getRepo() {
        mavenHttpRepo
    }

    @Override
    String getRepoDeclaration() {
        """
            repositories {
                maven {
                    url "$repo.uri"
                }
            }
        """
    }

    @Override
    Module setupCustomVariantsForModule(Module module) {
        return ((MavenModule) module).withModuleMetadata().variant("customVariant", [format: "custom"])
    }

    @Override
    String getVariantToTest() {
        return 'customVariant'
    }

    @Override
    boolean getPublishedModulesHaveAttributes() {
        return true
    }
}
