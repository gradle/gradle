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

import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.gradle.FileSpec
import org.gradle.test.fixtures.gradle.VariantMetadataSpec
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
    private final List<Object> excludeFromConfig = []
    private final List<Object> constraints = []
    private final List<VariantMetadataSpec> variants = []
    private final List<Closure<?>> withModule = []
    private final Map<String, String> componentLevelAttributes = [:]
    private List<InteractionExpectation> expectGetMetadata = [InteractionExpectation.NONE]
    private List<ArtifactExpectation> expectGetArtifact = []
    private MetadataType metadataType = MetadataType.REPO_DEFAULT

    static enum MetadataType {
        REPO_DEFAULT,
        GRADLE,
        LEGACY
    }

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

    void maybeHeadOrGetArtifact(Map<String, String> artifact) {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.MAYBE, artifact)
    }

    void maybeGetMetadata() {
        expectGetMetadata << InteractionExpectation.MAYBE
    }

    void expectGetVariantArtifacts(String variant) {
        expectGetArtifact << new ArtifactExpectation(InteractionExpectation.GET, new VariantArtifacts(variant))
    }

    void attribute(String key, String value) {
        componentLevelAttributes[key] = value
    }

    void variant(String variant, Map<String, String> attributes) {
        variants << new VariantMetadataSpec(variant, attributes)
    }

    void variant(String name, @DelegatesTo(value = VariantMetadataSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def variant = variants.find { it.name == name }
        if (variant == null) {
            variant = new VariantMetadataSpec(name)
            variants << variant
        }
        spec.delegate = variant
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    void variants(List<String> names, @DelegatesTo(value = VariantMetadataSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        names.each { name ->
            variant(name, spec)
        }
    }

    void asPlatform() {
        variant('apiElements') {
            useDefaultArtifacts = false
            noArtifacts = true
            attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_API)
            attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
        }
        variant('runtimeElements') {
            useDefaultArtifacts = false
            noArtifacts = true
            attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_RUNTIME)
            attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
        }
        withModule(MavenModule) {
            hasPackaging("pom")
        }
    }

    void dependsOn(coord) {
        dependsOn << coord
    }

    void excludeFromConfig(String module, String conf) {
        excludeFromConfig << [module: module, conf: conf]
    }

    void constraint(coord) {
        constraints << coord
    }

    void withoutGradleMetadata() {
        metadataType = MetadataType.LEGACY
    }

    void withGradleMetadata() {
        metadataType = MetadataType.GRADLE
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

    void build(HttpRepository repository) {
        def module = repository.module(groupId, artifactId, version)
        def legacyMetadataIsRequested = repository.providesMetadata != HttpRepository.MetadataType.ONLY_GRADLE
        def gradleMetadataWasPublished = metadataType == MetadataType.GRADLE || (metadataType == MetadataType.REPO_DEFAULT  && repository.providesMetadata != HttpRepository.MetadataType.ONLY_ORIGINAL)
        if (gradleMetadataWasPublished) {
            module.withModuleMetadata()
        }
        expectGetMetadata.each {
            switch (it) {
                case InteractionExpectation.NONE:
                    break
                case InteractionExpectation.MAYBE:
                    if (module instanceof MavenModule) {
                        module.pom.allowGetOrHead()
                    } else if (module instanceof IvyModule) {
                        module.ivy.allowGetOrHead()
                    }
                    module.moduleMetadata.allowGetOrHead()
                    break
                case InteractionExpectation.HEAD:
                    if (legacyMetadataIsRequested) {
                        if (module instanceof MavenModule) {
                            module.pom.expectHead()
                        } else if (module instanceof IvyModule) {
                            module.ivy.expectHead()
                        }
                    }
                    if (gradleMetadataWasPublished) {
                        module.moduleMetadata.expectHead()
                    }
                    break
                case InteractionExpectation.GET_MISSING:
                case InteractionExpectation.GET_MISSING_FOUND_ELSEWHERE:
                    // Assume all metadata files are missing
                    if (legacyMetadataIsRequested) {
                        if (module instanceof MavenModule) {
                            module.pom.expectGetMissing()
                        } else if (module instanceof IvyModule) {
                            module.ivy.expectGetMissing()
                        }
                    } else {
                        module.moduleMetadata.expectGetMissing()
                    }
                    break
                default:
                    if (legacyMetadataIsRequested) {
                        if (module instanceof MavenModule) {
                            module.pom.expectGet()
                        } else if (module instanceof IvyModule) {
                            module.ivy.expectGet()
                        }
                    }
                    if (gradleMetadataWasPublished) {
                        module.moduleMetadata.expectGet()
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
                        artifacts << module.getArtifact(expectation.spec)
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
                    attributes = attributes ? attributes + variant.attributes : variant.attributes
                    artifacts = variant.artifacts.collect {
                        if (it.name && it.name == it.url && it.name == it.publishUrl) {
                            // publish variant files as "classified". This can be arbitrary in practice, this
                            // just makes it easier for publishing specs
                            new FileSpec("${module.module}-${module.version}-$it.name.${it.ext}")
                        } else {
                            new FileSpec(it.name, it.url, it.publishUrl)
                        }
                    }
                    dependencies += variant.dependencies
                    dependencyConstraints += variant.dependencyConstraints
                    capabilities += variant.capabilities
                    availableAt = variant.availableAt
                    if (variant.noArtifacts) {
                        artifacts = []
                        useDefaultArtifacts = false
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

        if(excludeFromConfig) {
            excludeFromConfig.each {
                def moduleParts = it.module.split(':') as List
                module.excludeFromConfig(moduleParts[0], moduleParts[1], it.conf)
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
