/*
 * Copyright 2019 the original author or authors.
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

class MavenPublishOptionalDependenciesJavaPluginIntegTest extends AbstractMavenPublishOptionalDependenciesJavaIntegTest {
    def "can publish java-library with optional feature using extension"() {
        mavenRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """
            
            java {
                registerFeature("feature") {
                    usingSourceSet(sourceSets.main)
                }
            }
            
            dependencies {
                featureImplementation 'org:optionaldep:1.0'
            }
        """

        when:
        run "publish"

        then:
        javaLibrary.parsedModuleMetadata.variant("api") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtime") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("featureApi") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("featureRuntime") {
            assert files*.name == ['publishTest-1.9.jar']
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedPom.scope('compile') {
            assertOptionalDependencies('org:optionaldep:1.0')
        }
        javaLibrary.parsedPom.hasNoScope('runtime')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "org.gradle.test:publishTest-feature:1.0"
            withModuleMetadata {
                expectFiles "publishTest-1.9.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                shouldFail {
                    // documents the current behavior
                    assertHasCause("Unable to find a variant of org.gradle.test:publishTest:1.9 providing the requested capability org.gradle.test:publishTest-feature:1.0")
                }
            }
        }
    }
}
