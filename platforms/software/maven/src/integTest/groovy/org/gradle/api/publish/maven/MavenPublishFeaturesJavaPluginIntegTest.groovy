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

class MavenPublishFeaturesJavaPluginIntegTest extends AbstractMavenPublishFeaturesJavaIntegTest {
    def "can publish java-library with feature using extension"() {
        mavenRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """

            sourceSets {
                feature
            }

            repositories { maven { url = "${mavenRepo.uri}" } }

            java {
                registerFeature("feature") {
                    usingSourceSet(sourceSets.feature)
                }
            }

            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }

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
            assert files*.name == ['publishTest-1.9-feature.jar']
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedPom.scope('runtime') {
            assertOptionalDependencies('org:optionaldep:1.0')
        }
        javaLibrary.parsedPom.hasNoScope('compile')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "org.gradle.test:publishTest-feature:1.0"
            withModuleMetadata {
                expectFiles "publishTest-1.9.jar", "publishTest-1.9-feature.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                shouldFail {
                    // documents the current behavior
                    assertHasCause("Unable to find a variant with the requested capability: coordinates 'org.gradle.test:publishTest-feature'")
                }
            }
        }
    }

    def "can update #prop after feature has been registered"() {
        mavenRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """

            sourceSets {
                feature
            }

            repositories { maven { url = "${mavenRepo.uri}" } }

            java {
                registerFeature("feature") {
                    usingSourceSet(sourceSets.feature)
                }
            }

            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }

            dependencies {
                featureImplementation 'org:optionaldep:1.0'
            }

            $prop = "$newValue"
        """

        when:
        def mod = mavenRepo.module(group, name, version)
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
            assert files*.name == ["${name}-${version}-feature.jar"]
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("featureRuntimeElements") {
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedPom.scope('runtime') {
            assertOptionalDependencies('org:optionaldep:1.0')
        }
        javaLibrary.parsedPom.hasNoScope('compile')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "${name}-${version}.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "${name}-${version}.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "${name}-${version}.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "$group:${name}-feature:1.0"
            withModuleMetadata {
                expectFiles "${name}-${version}.jar", "${name}-${version}-feature.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                shouldFail {
                    // documents the current behavior
                    assertHasCause("Unable to find a variant with the requested capability: coordinates '$group:${name}-feature'")
                }
            }
        }

        where:
        prop      | group             | name          | version | newValue
        'group'   | 'newgroup'        | 'publishTest' | '1.9'   | 'newgroup'
        'version' | 'org.gradle.test' | 'publishTest' | '2.0'   | '2.0'
    }

}
