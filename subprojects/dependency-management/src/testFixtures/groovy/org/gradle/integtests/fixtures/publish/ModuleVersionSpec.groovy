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
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule

class ModuleVersionSpec {
    private final String groupId
    private final String artifactId
    private final String version
    private final boolean mustPublish = !RemoteRepositorySpec.DEFINES_INTERACTIONS.get()

    private final List<Object> dependsOn = []
    private final List<Object> constraints = []
    private final Map<String, Map<String, String>> variants = [:]
    private final List<Closure<?>> withModule = []
    private List<InteractionExpectation> expectGetMetadata = [InteractionExpectation.NONE]
    private List<ArtifactExpectation> expectGetArtifact = []

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

    void variant(String variant, Map<String, String> attributes) {
        variants << [(variant): attributes]
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
                spec()
            }
        }
    }

    void allowAll() {
        withModule {
            delegate.allowAll()
        }
    }

    void build(HttpRepository repository) {
        def module = repository.module(groupId, artifactId, version)
        def gradleMetadataEnabled = GradleMetadataResolveRunner.isGradleMetadataEnabled()
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
                    if (newResolveBehaviorEnabled) {
                        module.moduleMetadata.expectGetMissing()
                    }

                    if (module instanceof MavenModule) {
                        module.pom.expectGetMissing()
                    } else if (module instanceof IvyModule) {
                        module.ivy.expectGetMissing()
                    }
                    break
                default:
                    if (newResolveBehaviorEnabled && !gradleMetadataEnabled) {
                        module.moduleMetadata.allowGetOrHead()
                    }
                    if (newResolveBehaviorEnabled && gradleMetadataEnabled) {
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
                def artifact
                if (expectation.spec) {
                    if (module instanceof MavenModule) {
                        artifact = module.getArtifact(expectation.spec)
                    } else if (module instanceof IvyModule) {
                        artifact = module.artifact(expectation.spec)
                    }
                } else {
                    artifact = module.artifact
                }
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
        if (variants) {
            variants.each {
                module.variant(it.key, it.value)
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

}
