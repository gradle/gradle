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

package org.gradle.integtests.publish.ivy

import org.gradle.api.publish.ivy.WithUploadArchives
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ArtifactResolutionExpectationSpec
import org.gradle.test.fixtures.GradleMetadataAwarePublishingSpec
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.SingleArtifactResolutionResultSpec
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.IvyJavaModule
import org.gradle.test.fixtures.ivy.IvyModule


abstract class AbstractIvyPublishIntegTest extends AbstractIntegrationSpec implements GradleMetadataAwarePublishingSpec, WithUploadArchives {

    def setup() {
        configureUploadTask()
    }

    protected static IvyJavaModule javaLibrary(IvyFileModule ivyFileModule) {
        return new IvyJavaModule(ivyFileModule)
    }

    void resolveArtifacts(Object dependencyNotation, @DelegatesTo(value = IvyArtifactResolutionExpectation, strategy = Closure.DELEGATE_FIRST) Closure<?> expectationSpec) {
        IvyArtifactResolutionExpectation expectation = new IvyArtifactResolutionExpectation(dependencyNotation)
        expectation.dependency = convertDependencyNotation(dependencyNotation)
        expectationSpec.resolveStrategy = Closure.DELEGATE_FIRST
        expectationSpec.delegate = expectation
        expectationSpec()

        expectation.validate()

    }

    void resolveApiArtifacts(IvyModule module, @DelegatesTo(value = IvyArtifactResolutionExpectation, strategy = Closure.DELEGATE_FIRST) Closure<?> expectationSpec) {
        resolveArtifacts(module) {
            variant = 'JAVA_API'
            expectationSpec.delegate = delegate
            expectationSpec()
        }
    }

    void resolveRuntimeArtifacts(IvyModule module, @DelegatesTo(value = IvyArtifactResolutionExpectation, strategy = Closure.DELEGATE_FIRST) Closure<?> expectationSpec) {
        resolveArtifacts(module) {
            variant = 'JAVA_RUNTIME'
            expectationSpec.delegate = delegate
            expectationSpec()
        }
    }

    private def doResolveArtifacts(ResolveParams params) {
        // Replace the existing buildfile with one for resolving the published module
        settingsFile.text = "rootProject.name = 'resolve'"
        def attributes = params.variant == null ?
            "" :
            """
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.${params.variant}))
    }
"""
        String extraArtifacts = ""
        if (params.additionalArtifacts) {
            String artifacts = params.additionalArtifacts.collect {
                def tokens = it.ivyTokens
                """
                    artifact {
                        name = '${sq(tokens.artifact)}'
                        classifier = '${sq(tokens.classifier)}'
                        type = '${sq(tokens.type)}'
                    }"""
            }.join('\n')
            extraArtifacts = """
                {
                    transitive = false
                    $artifacts
                }
            """
        }

        String dependencyNotation = params.dependency
        if (params.classifier) {
            dependencyNotation = "${dependencyNotation}, classifier: '${sq(params.classifier)}'"
        }
        if (params.ext) {
            dependencyNotation = "${dependencyNotation}, ext: '${sq(params.ext)}'"
        }

        def optional = params.optionalFeatureCapabilities.collect {
            "resolve($dependencyNotation) { capabilities { requireCapability('$it') } }"
        }.join('\n')
        buildFile.text = """import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport

            apply plugin: 'java-base' // to get the standard Java library derivation strategy
            configurations {
                resolve {
                    ${attributes}
                }
            }
            repositories {
                ivy {
                    url "${ivyRepo.uri}"
                    metadataSources {
                        ${params.resolveModuleMetadata?'gradleMetadata':'ivyDescriptor'}()
                        ${params.resolveModuleMetadata?'':'ignoreGradleMetadataRedirection()'}
                    }
                }
            }

            dependencies {
               resolve($dependencyNotation) $extraArtifacts
               $optional
            }

            task resolveArtifacts(type: Sync) {
                outputs.upToDateWhen { false }
                from configurations.resolve
                into "artifacts"
            }

"""

        if (params.expectFailure) {
            fails("resolveArtifacts")
            return failure
        }
        run("resolveArtifacts")
        def artifactsList = file("artifacts").exists() ? file("artifacts").list() : []
        return artifactsList.sort()
    }

    static class ResolveParams {
        IvyModule module
        String dependency
        List<? extends ModuleArtifact> additionalArtifacts

        String classifier
        String ext
        String variant
        boolean resolveModuleMetadata
        boolean expectFailure

        List<String> optionalFeatureCapabilities = []
    }

    class IvyArtifactResolutionExpectation extends ResolveParams implements ArtifactResolutionExpectationSpec<IvyModule> {

        IvyArtifactResolutionExpectation(Object dependencyNotation) {
            if (dependencyNotation instanceof IvyModule) {
                module = dependencyNotation
            }
            createSpecs()
        }

        IvyModule getModule() {
            super.module
        }

        void validate() {
            singleValidation(false, withoutModuleMetadataSpec)
            singleValidation(true, withModuleMetadataSpec)
        }

        void singleValidation(boolean withModuleMetadata, SingleArtifactResolutionResultSpec expectationSpec) {
            ResolveParams params = new ResolveParams(
                module: module,
                dependency: dependency,
                classifier: classifier,
                ext: ext,
                additionalArtifacts: additionalArtifacts?.asImmutable(),
                variant: variant,
                resolveModuleMetadata: withModuleMetadata,
                expectFailure: !expectationSpec.expectSuccess,
                optionalFeatureCapabilities: optionalFeatureCapabilities,
            )
            println "Checking ${additionalArtifacts?'additional artifacts':'artifacts'} when resolving ${withModuleMetadata?'with':'without'} Gradle module metadata"
            def resolutionResult = doResolveArtifacts(params)
            expectationSpec.with {
                if (expectSuccess) {
                    assert resolutionResult == expectedFileNames
                } else {
                    failureExpectations.each {
                        it.resolveStrategy = Closure.DELEGATE_FIRST
                        it.delegate = resolutionResult
                        it()
                    }
                }
            }
        }

    }
}
