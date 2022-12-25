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

package org.gradle.api.publish.ivy

class IvyPublishFeaturesJavaPluginIntegTest extends AbstractIvyPublishFeaturesJavaIntegTest {
    def "can publish java-library with feature using extension"() {
        ivyRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """

            java {
                registerFeature("feature") {
                    usingSourceSet(sourceSets.main)
                }
            }

            ${ivyTestRepository()}

            dependencies {
                featureImplementation 'org:optionaldep:1.0'
            }
        """

        when:
        run "publish"

        then:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("featureApiElements") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("featureRuntimeElements") {
            assert files*.name == ['publishTest-1.9.jar']
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedIvy.dependencies.size() == 1
        javaLibrary.parsedIvy.dependencies['org:optionaldep:1.0'].hasConf('featureRuntimeElements->default')

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
                // documents the current behavior - ivy does not use variant matching and hence the requested capability is ignored and the default configuration is selected
                expectFiles "publishTest-1.9.jar"
            }
        }
    }

    def "can update #prop after feature has been registered"() {
        ivyRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """

            java {
                registerFeature("feature") {
                    usingSourceSet(sourceSets.main)
                }
            }

            ${ivyTestRepository()}

            dependencies {
                featureImplementation 'org:optionaldep:1.0'
            }

            $prop = "$newValue"
        """

        when:
        def mod = ivyRepo.module(group, name, version)
        javaLibrary = javaLibrary(mod)
        run "publish"
        mod.removeGradleMetadataRedirection()

        then:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("featureApiElements") {
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("featureRuntimeElements") {
            assert files*.name == ["${name}-${version}.jar"]
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedIvy.dependencies.size() == 1
        javaLibrary.parsedIvy.dependencies['org:optionaldep:1.0'].hasConf('featureRuntimeElements->default')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "${name}-${version}.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "${name}-${version}.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "${name}-${version}.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "$group:${name}-feature:1.0"
            withModuleMetadata {
                expectFiles "${name}-${version}.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                // documents the current behavior - ivy does not use variant matching and hence the requested capability is ignored and the default configuration is selected
                expectFiles "${name}-${version}.jar"
            }
        }

        where:
        prop      | group             | name          | version | newValue
        'group'   | 'newgroup'        | 'publishTest' | '1.9'   | 'newgroup'
        'version' | 'org.gradle.test' | 'publishTest' | '2.0'   | '2.0'
    }

}
