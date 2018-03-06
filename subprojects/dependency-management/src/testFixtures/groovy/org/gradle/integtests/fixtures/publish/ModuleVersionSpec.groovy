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

package org.gradle.integtests.fixtures.publish

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.gradle.CapabilitySpec
import org.gradle.test.fixtures.gradle.FileSpec
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.server.http.HttpArtifact
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpModule

class ModuleVersionSpec {
    private final String groupId
    private final String artifactId
    private final String version
    private final boolean mustPublish = !RemoteRepositorySpec.DEFINES_INTERACTIONS.get()

    private final List<Object> dependsOn = []
    private final List<Object> constraints = []
    private final List<VariantSpec> variants = []
    private final List<Closure<?>> withModule = []
    private final Map<String, String> componentLevelAttributes = [:]
    private List<InteractionExpectation> expectGetMetadata = [InteractionExpectation.NONE]
    private List<ArtifactExpectation> expectGetArtifact = []
    private Map<String, CapabilitySpec> capabilities = [:].withDefault { new CapabilitySpec(it) }

    static class ArtifactExpectation {
        final InteractionExpectation type
        final Object spec

        ArtifactExpectation(InteractionExpectation type, Object spec) {
            this.type = type
            this.spec = spec
        }
    }

    ModuleVersionSpec(String group, String module, String version) {
        this.groupId = group
        this.artifactId = module
        this.version = version
    }

    void expectResolve() {
        expectGetMetadata()
        expectGetArtifact()
    }

    void expectGetMetadata() {
        expectGetMetadata << InteractionExpectation.GET
    }

    void expectGetMetadataMissing() {
        expectGetMetadata << InteractionExpectation.GET_MISSING
    }

    void expectGetMetadataMissingThatIsFoundElsewhere() {
        expectGetMetadata << InteractionExpectation.GET_MISSING_FOUND_ELSEWHERE
    }

    void expectHeadMetadata() {
        expectGetMetadata << InteractionExpectation.HEAD
    }

    void expectGetArtifact(String artifact = '') {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.GET, artifact)
    }

    void expectGetArtifact(Map<String, String> artifact) {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.GET, artifact)
    }

    void expectHeadArtifact(String artifact = '') {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.HEAD, artifact)
    }

    void expectHeadArtifact(Map<String, String> artifact) {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.HEAD, artifact)
    }

    void expectHeadArtifactMissing(String artifact = '') {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.HEAD_MISSING, artifact)
    }

    void maybeGetMetadata() {
        expectGetMetadata << InteractionExpectation.MAYBE
    }

    void expectGetVariantArtifacts(String variant) {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.GET, new VariantArtifacts(variant))
    }

    void capability(String name, Closure<?> config) {
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.delegate = capabilities[name]
        config()
    }

    void attribute(String key, String value) {
        componentLevelAttributes[key] = value
    }

    void variant(String variant, Map<String, String> attributes) {
        variants << new VariantSpec(name:variant, attributes:attributes)
    }

    void variant(String name, @DelegatesTo(value= VariantSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def variant = new VariantSpec(name: name)
        spec.delegate = variant
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        variants << variant
    }

    void dependsOn(coord) {
        dependsOn << coord
    }

    void constraint(coord) {
        constraints << coord
    }

    void withModule(@DelegatesTo(HttpModule) Closure<?> spec) {
        withModule << spec
    }

    public <T extends Module> void withModule(Class<T> moduleClass, @DelegatesTo(type = "T") Closure<?> spec) {
        withModule << { ->
            if (moduleClass.isAssignableFrom(delegate.class)) {
                spec.delegate = delegate
                spec.resolveStrategy = Closure.DELEGATE_FIRST
                spec()
            }
        }
    }

    void allowAll() {
        withModule {
            delegate.allowAll()
        }
    }

    private static boolean hasGradleMetadata(HttpRepository repository) {
        if (repository.providesMetadata == HttpRepository.MetadataType.ONLY_ORIGINAL) {
            return false
        }
        if (repository.providesMetadata == HttpRepository.MetadataType.ONLY_GRADLE) {
            return true
        }
        return GradleMetadataResolveRunner.isGradleMetadataEnabled()
    }

    void build(HttpRepository repository) {
        def module = repository.module(groupId, artifactId, version)
        def gradleMetadataEnabled = hasGradleMetadata(repository)
        def newResolveBehaviorEnabled = GradleMetadataResolveRunner.isExperimentalResolveBehaviorEnabled()
        if (gradleMetadataEnabled) {
            module.withModuleMetadata()
        }
        expectGetMetadata.each {
            switch (it) {
                case InteractionExpectation.NONE:
                    break
                case InteractionExpectation.MAYBE:
                    if (newResolveBehaviorEnabled) {
                        module.moduleMetadata.allowGetOrHead()
                    } else if (module instanceof MavenModule) {
                        module.pom.allowGetOrHead()
                    } else if (module instanceof IvyModule) {
                        module.ivy.allowGetOrHead()
                    }
                    break
                case InteractionExpectation.HEAD:
                    if (newResolveBehaviorEnabled && !gradleMetadataEnabled) {
                        module.moduleMetadata.allowGetOrHead()
                    }
                    if (newResolveBehaviorEnabled && gradleMetadataEnabled) {
                        module.moduleMetadata.expectHead()
                    } else if (module instanceof MavenModule) {
                        module.pom.expectHead()
                    } else if (module instanceof IvyModule) {
                        module.ivy.expectHead()
                    }

                    break
                case InteractionExpectation.GET_MISSING:
                    // Assume all metadata files are missing
                    if (newResolveBehaviorEnabled && repository.providesMetadata != HttpRepository.MetadataType.ONLY_ORIGINAL) {
                        module.moduleMetadata.expectGetMissing()
                    }

                    if (repository.providesMetadata == HttpRepository.MetadataType.ONLY_GRADLE) {
                        break
                    }

                    if (module instanceof MavenModule) {
                        module.pom.expectGetMissing()
                    } else if (module instanceof IvyModule) {
                        module.ivy.expectGetMissing()
                    }
                    break
                case InteractionExpectation.GET_MISSING_FOUND_ELSEWHERE:
                    if (newResolveBehaviorEnabled || gradleMetadataEnabled) {
                        module.moduleMetadata.expectGetMissing()
                    } else if (module instanceof MavenModule) {
                        module.pom.expectGetMissing()
                    } else if (module instanceof IvyModule) {
                        module.ivy.expectGetMissing()
                    }
                    break
                default:
                    if (newResolveBehaviorEnabled && !gradleMetadataEnabled) {
                        module.moduleMetadata.allowGetOrHead()
                    }
                    if (gradleMetadataEnabled) {
                        module.moduleMetadata.expectGet()
                    } else if (module instanceof MavenModule) {
                        module.pom.expectGet()
                    } else if (module instanceof IvyModule) {
                        module.ivy.expectGet()
                    }
            }
        }

        if (expectGetArtifact) {
            expectGetArtifact.each { ArtifactExpectation expectation ->
                def artifacts = []
                if (expectation.spec) {
                    if (expectation.spec instanceof VariantArtifacts) {
                        artifacts.addAll(expectation.spec.toArtifacts(module))
                    } else if (module instanceof MavenModule) {
                        artifacts << module.getArtifact(expectation.spec)
                    } else if (module instanceof IvyModule) {
                        artifacts << module.artifact(expectation.spec)
                    }
                } else {
                    artifacts << module.artifact
                }
                artifacts.each { artifact ->
                    switch (expectation.type) {
                        case InteractionExpectation.GET:
                            artifact.expectGet()
                            break
                        case InteractionExpectation.HEAD:
                            artifact.expectHead()
                            break
                        case InteractionExpectation.HEAD_MISSING:
                            artifact.expectHeadMissing()
                            break
                        case InteractionExpectation.MAYBE:
                            artifact.allowGetOrHead()
                            break
                        case InteractionExpectation.NONE:
                            break
                    }
                }
            }
        }
        if (componentLevelAttributes) {
            componentLevelAttributes.each { key, value ->
                module.attributes[key] = value
            }
        }
        if (variants) {
            variants.each { variant ->
                module.withVariant(variant.name) {
                    attributes = attributes? attributes + variant.attributes : variant.attributes
                    artifacts = variant.artifacts.collect {
                        // publish variant files as "classified". This can be arbitrary in practice, this
                        // just makes it easier for publishing specs
                        new FileSpec("${module.module}-${module.version}-$it.name.${it.ext}", it.url)
                    }
                    variant.dependsOn.each {
                        def args = it.split(':') as List
                        dependsOn(*args)
                    }
                }
            }
        }

        if (dependsOn) {
            dependsOn.each {
                if (it instanceof CharSequence) {
                    def args = it.split(':') as List
                    module.dependsOn(repository.module(*args))
                } else if (it instanceof Map) {
                    def other = repository.module(it.group, it.artifact, it.version)
                    module.dependsOn(it, other)
                } else {
                    module.dependsOn(it)
                }
            }
        }
        if (constraints) {
            constraints.each {
                if (it instanceof CharSequence) {
                    def args = it.split(':') as List
                    module.dependencyConstraint(repository.module(*args))
                } else if (it instanceof Map) {
                    def other = repository.module(it.group, it.artifact, it.version)
                    module.dependencyConstraint(it, other)
                } else {
                    module.dependencyConstraint(it)
                }
            }
        }
        if (capabilities) {
            capabilities.values().each {
                module.addCapability(it)
            }
        }
        if (withModule) {
            withModule.each { spec ->
                spec.delegate = module
                spec()
            }
        }
        if (mustPublish) {
            // do not publish modules created during a `repositoryInteractions { ... }` block
            module.publish()
        }
    }

    class VariantArtifacts {
        final String variant

        VariantArtifacts(String name) {
            this.variant = name
        }

        List<HttpArtifact> toArtifacts(IvyHttpModule ivyModule) {
            variants.find { it.name == variant }.artifacts.collect {
                ivyModule.getArtifact(classifier: it.name, ext: it.ext)
            }
        }

        List<HttpArtifact> toArtifacts(MavenHttpModule mavenModule) {
            variants.find { it.name == variant }.artifacts.collect {
                mavenModule.getArtifact(classifier: it.name, type: it.ext)
            }
        }
    }

}
