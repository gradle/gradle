/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.integtests.fixtures.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ArtifactResolutionExpectationSpec
import org.gradle.test.fixtures.GradleMetadataAwarePublishingSpec
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.SingleArtifactResolutionResultSpec
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenJavaModule
import org.gradle.test.fixtures.maven.MavenJavaPlatformModule
import org.gradle.test.fixtures.maven.MavenModule

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

abstract class AbstractMavenPublishIntegTest extends AbstractIntegrationSpec implements GradleMetadataAwarePublishingSpec {

    protected static MavenJavaModule javaLibrary(MavenFileModule mavenFileModule, List<String> features = [MavenJavaModule.MAIN_FEATURE], boolean withDocumentation = false) {
        return new MavenJavaModule(mavenFileModule, features, withDocumentation)
    }

    protected static MavenJavaPlatformModule javaPlatform(MavenFileModule mavenFileModule) {
        return new MavenJavaPlatformModule(mavenFileModule)
    }

    void resolveArtifacts(Object dependencyNotation, @DelegatesTo(value = MavenArtifactResolutionExpectation, strategy = Closure.DELEGATE_FIRST) Closure<?> expectationSpec) {
        MavenArtifactResolutionExpectation expectation = new MavenArtifactResolutionExpectation(dependencyNotation, this)
        expectation.dependency = convertDependencyNotation(dependencyNotation)
        expectationSpec.resolveStrategy = Closure.DELEGATE_FIRST
        expectationSpec.delegate = expectation
        expectationSpec()

        expectation.validate()

    }

    void resolveApiArtifacts(MavenModule module, @DelegatesTo(value = MavenArtifactResolutionExpectation, strategy = Closure.DELEGATE_FIRST) Closure<?> expectationSpec) {
        resolveArtifacts(module) {
            variant = 'JAVA_API'
            expectationSpec.delegate = delegate
            expectationSpec()
        }
    }

    void resolveRuntimeArtifacts(MavenModule module, @DelegatesTo(value = MavenArtifactResolutionExpectation, strategy = Closure.DELEGATE_FIRST) Closure<?> expectationSpec) {
        resolveArtifacts(module) {
            variant = 'JAVA_RUNTIME'
            expectationSpec.delegate = delegate
            expectationSpec()
        }
    }

    def doResolveArtifacts(ResolveParams params) {
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

        def externalRepo = requiresExternalDependencies?mavenCentralRepositoryDefinition():''
        def optional = params.optionalFeatureCapabilities.collect {
            "resolve($dependencyNotation) { capabilities { requireCapability('$it') } }"
        }.join('\n')
        buildFile.text = """
            apply plugin: 'java-base' // to get the standard Java library derivation strategy
            configurations {
                resolve {
                    ${attributes}
                }
            }
            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                    metadataSources {
                        ${params.resolveModuleMetadata?'gradleMetadata':'mavenPom'}()
                        ${params.resolveModuleMetadata?'':'ignoreGradleMetadataRedirection()'}
                    }
                }
                ${externalRepo}
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
        MavenModule module
        String dependency
        List<? extends ModuleArtifact> additionalArtifacts

        String classifier
        String ext
        String variant
        boolean resolveModuleMetadata
        boolean expectFailure

        List<String> optionalFeatureCapabilities = []
    }

    class MavenArtifactResolutionExpectation extends ResolveParams implements ArtifactResolutionExpectationSpec<MavenModule> {

        private final AbstractMavenPublishIntegTest test

        MavenArtifactResolutionExpectation(Object dependencyNotation, AbstractMavenPublishIntegTest test) {
            this.test = test
            if (dependencyNotation instanceof MavenModule) {
                module = dependencyNotation
            }
            createSpecs()
        }

        MavenModule getModule() {
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
            def resolutionResult = test.doResolveArtifacts(params)
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
