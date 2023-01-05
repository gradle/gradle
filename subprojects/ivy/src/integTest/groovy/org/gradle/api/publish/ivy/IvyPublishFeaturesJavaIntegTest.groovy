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

import spock.lang.Unroll

class IvyPublishFeaturesJavaIntegTest extends AbstractIvyPublishFeaturesJavaIntegTest {

    def "can publish java-library with a feature"() {
        ivyRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS))
                    }
                    outgoing.capability("org:optional-feature:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }

            ${ivyTestRepository()}

            dependencies {
                optionalFeatureImplementation 'org:optionaldep:1.0'
            }

            components.java.addVariantsFromConfiguration(configurations.optionalFeatureRuntimeElements) {
                mapToOptional()
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
        javaLibrary.parsedModuleMetadata.variant("optionalFeatureRuntimeElements") {
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedIvy.dependencies.size() == 1
        javaLibrary.parsedIvy.dependencies['org:optionaldep:1.0'].hasConf('optionalFeatureRuntimeElements->default')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "org:optional-feature:1.0"
            withModuleMetadata {
                expectFiles "publishTest-1.9.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                // documents the current behavior - ivy does not use variant matching and hence the requested capability is ignored and the default configuration is selected
                expectFiles "publishTest-1.9.jar"
            }
        }
    }

    def "can group dependencies by feature"() {
        ivyRepo.module('org', 'optionaldep-g1', '1.0').publish()
        ivyRepo.module('org', 'optionaldep1-g2', '1.0').publish()
        ivyRepo.module('org', 'optionaldep2-g2', '1.0').publish()

        given:
        buildFile << """
            configurations {
                optionalFeature1Implementation
                optionalFeature1RuntimeElements {
                    extendsFrom optionalFeature1Implementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS))
                    }
                    outgoing.capability("org:optional-feature1:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeature1Implementation)

                optionalFeature2Implementation
                optionalFeature2RuntimeElements {
                    extendsFrom optionalFeature2Implementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS))
                    }
                    outgoing.capability("org:optional-feature2:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeature2Implementation)
            }

            ${ivyTestRepository()}

            dependencies {
                optionalFeature1Implementation 'org:optionaldep-g1:1.0'
                optionalFeature2Implementation 'org:optionaldep1-g2:1.0'
                optionalFeature2Implementation 'org:optionaldep2-g2:1.0'
            }

            components.java.addVariantsFromConfiguration(configurations.optionalFeature1RuntimeElements) {
                mapToOptional()
            }
            components.java.addVariantsFromConfiguration(configurations.optionalFeature2RuntimeElements) {
                mapToOptional()
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
        javaLibrary.parsedModuleMetadata.variant("optionalFeature1RuntimeElements") {
            dependency('org', 'optionaldep-g1', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("optionalFeature2RuntimeElements") {
            dependency('org', 'optionaldep1-g2', '1.0')
            dependency('org', 'optionaldep2-g2', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedIvy.dependencies.size() == 3
        javaLibrary.parsedIvy.dependencies['org:optionaldep-g1:1.0'].hasConf('optionalFeature1RuntimeElements->default')
        javaLibrary.parsedIvy.dependencies['org:optionaldep1-g2:1.0'].hasConf('optionalFeature2RuntimeElements->default')
        javaLibrary.parsedIvy.dependencies['org:optionaldep2-g2:1.0'].hasConf('optionalFeature2RuntimeElements->default')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
    }

    @Unroll("publish java-library with feature with additional artifact #id (#optionalFeatureFileName)")
    def "publish java-library with feature with additional artifact"() {
        ivyRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS))
                    }
                    outgoing.capability("org:optional-feature:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }

            ${ivyTestRepository()}

            dependencies {
                optionalFeatureImplementation 'org:optionaldep:1.0'
            }

            components.java.addVariantsFromConfiguration(configurations.optionalFeatureRuntimeElements) {
                mapToOptional()
            }

            artifacts {
                if ('$classifier' == 'null') {
                    optionalFeatureRuntimeElements file:file("\$buildDir/$optionalFeatureFileName"), builtBy:'touchFile'
                } else {
                    optionalFeatureRuntimeElements file:file("\$buildDir/$optionalFeatureFileName"), builtBy:'touchFile', classifier: '$classifier'
                }
            }

            task touchFile {
                // explicit dependency otherwise this task may run before the Jar task
                dependsOn tasks.jar
                def featureFile = file("\$buildDir/$optionalFeatureFileName")
                doLast {
                    featureFile << "test"
                }
            }
        """

        when:
        if (failureText) {
            fails "publish"
        } else {
            run "publish"
        }

        then:
        if (failureText) {
            failure.assertHasCause(failureText)
        } else {
            javaLibrary.withClassifiedArtifact("optional-feature", "jar")
            javaLibrary.parsedModuleMetadata.variant("apiElements") {
                assert files*.name == ["publishTest-1.9.jar"]
                noMoreDependencies()
            }
            javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
                assert files*.name == ["publishTest-1.9.jar"]
                noMoreDependencies()
            }
            javaLibrary.parsedModuleMetadata.variant("optionalFeatureRuntimeElements") {
                assert files*.name == ["publishTest-1.9-optional-feature.jar"]
                dependency('org', 'optionaldep', '1.0')
                noMoreDependencies()
            }
            assert javaLibrary.parsedIvy.dependencies['org:optionaldep:1.0'].hasConf('optionalFeatureRuntimeElements->default')
            assert javaLibrary.parsedIvy.artifacts.size() == 2
            javaLibrary.parsedIvy.artifacts[0].hasAttributes("jar", "jar", ["compile", "runtime"])
            javaLibrary.parsedIvy.artifacts[1].hasAttributes("jar", "jar", ["optionalFeatureRuntimeElements"], "optional-feature")

            resolveRuntimeArtifacts(javaLibrary) {
                optionalFeatureCapabilities << "org:optional-feature:1.0"
                withModuleMetadata {
                    expectFiles "publishTest-1.9.jar", "optionaldep-1.0.jar", "publishTest-1.9-optional-feature.jar"
                }
                withoutModuleMetadata {
                    // documents the current behavior - ivy does not use variant matching and hence the requested capability is ignored and the default configuration is selected
                    expectFiles "publishTest-1.9.jar"
                }
            }
        }

        where:
        id                                                   | optionalFeatureFileName                | classifier         | failureText
        "with the same name and an implicit classifier"      | "publishTest-1.9-optional-feature.jar" | null               | null
        "with an arbitrary name and no classifier"           | "optional-feature-1.9.jar"             | null               | "Invalid publication 'ivy': multiple artifacts with the identical name, extension, type and classifier ('publishTest', jar', 'jar', 'null'"
        "with an arbitrary name and a classifier"            | "other-1.9.jar"                        | "optional-feature" | null
        "with an arbitrary name with an implicit classifier" | "other-1.9-optional-feature.jar"       | null               | null
        "with the same name and no classifier"               | "publishTest-1.9.jar"                  | null               | "Invalid publication 'ivy': multiple artifacts with the identical name, extension, type and classifier ('publishTest', jar', 'jar', 'null'"
        "with the same name and a classifier"                | "publishTest-1.9.jar"                  | "optional-feature" | null

    }

    def "can publish java-library with a feature from a configuration with more than one outgoing variant"() {
        ivyRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS))
                    }
                    outgoing.capability("org:optional-feature:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }

            ${ivyTestRepository()}

            dependencies {
                optionalFeatureImplementation 'org:optionaldep:1.0'
            }

            components.java.addVariantsFromConfiguration(configurations.optionalFeatureRuntimeElements) {
                mapToOptional()
            }

            def alt = configurations.optionalFeatureRuntimeElements.outgoing.variants.create("alternate")
            alt.attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'java-runtime-alt'))
            }
            def altFile = file("\${buildDir}/\${name}-\${version}-alt.jar")
            task createFile { doFirst { altFile.parentFile.mkdirs(); altFile.text = "test file" } }
            alt.artifact(file:altFile, builtBy: 'createFile')

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
        javaLibrary.parsedModuleMetadata.variant("optionalFeatureRuntimeElements") {
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("optionalFeatureRuntimeElementsAlternate") {
            dependency('org', 'optionaldep', '1.0')
            noMoreDependencies()
        }
        javaLibrary.parsedIvy.dependencies['org:optionaldep:1.0'].hasConf('optionalFeatureRuntimeElements->default')
        javaLibrary.parsedIvy.dependencies['org:optionaldep:1.0'].hasConf('optionalFeatureRuntimeElementsAlternate->default')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "org:optional-feature:1.0"
            withModuleMetadata {
                expectFiles "publishTest-1.9.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                // documents the current behavior - ivy does not use variant matching and hence the requested capability is ignored and the default configuration is selected
                expectFiles "publishTest-1.9.jar"
            }
        }
    }

    def "can publish java-library with a feature from a configuration with more than one outgoing variant and filter out variants"() {
        ivyRepo.module('org', 'optionaldep', '1.0').withModuleMetadata().publish()

        given:
        buildFile << """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                    outgoing.capability("org:optional-feature:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }

            ${ivyTestRepository()}

            dependencies {
                optionalFeatureImplementation 'org:optionaldep:1.0'
            }

            components.java.addVariantsFromConfiguration(configurations.optionalFeatureRuntimeElements) {
                if (it.configurationVariant.name != 'alternate') {
                    skip()
                } else {
                    mapToOptional()
                }
            }

            def alt = configurations.optionalFeatureRuntimeElements.outgoing.variants.create("alternate")
            alt.attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'java-runtime'))
            }
            def altFile = file("\${buildDir}/\${name}-\${version}-alt.jar")
            task createFile { doFirst { altFile.parentFile.mkdirs(); altFile.text = "test file" } }
            alt.artifact(file:altFile, builtBy: 'createFile')

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
        !javaLibrary.parsedModuleMetadata.variants.name.contains('optionalFeatureRuntimeElements')
        javaLibrary.parsedModuleMetadata.variant("optionalFeatureRuntimeElementsAlternate") {
            dependency('org', 'optionaldep', '1.0')
            files.name == ['publishTest-1.9-alt.jar']
            noMoreDependencies()
        }
        javaLibrary.parsedIvy.dependencies['org:optionaldep:1.0'].hasConf('optionalFeatureRuntimeElementsAlternate->default')
        !javaLibrary.parsedIvy.configurations.containsKey('optionalFeatureRuntimeElements')

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }

        resolveRuntimeArtifacts(javaLibrary) {
            optionalFeatureCapabilities << "org:optional-feature:1.0"
            withModuleMetadata {
                // the first file comes from the fact the test fixture adds 2 dependencies (main component, optional feature)
                expectFiles "publishTest-1.9.jar", "publishTest-1.9-alt.jar", "optionaldep-1.0.jar"
            }
            withoutModuleMetadata {
                // documents the current behavior - ivy does not use variant matching and hence the requested capability is ignored and the default configuration is selected
                expectFiles "publishTest-1.9.jar"
            }
        }
    }
}
