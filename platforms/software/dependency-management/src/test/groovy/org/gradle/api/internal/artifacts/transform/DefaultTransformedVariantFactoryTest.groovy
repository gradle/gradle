/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.UnavailableResolvedArtifactSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultTransformedVariantFactoryTest extends Specification {

    def sharedCache = new SharedTransformedVariantCache()
    def startParameter = new StartParameterInternal()

    def newFactory() {
        new DefaultTransformedVariantFactory(
            new TestBuildOperationRunner(),
            TestUtil.calculatedValueContainerFactory(),
            Mock(TransformStepNodeFactory),
            sharedCache,
            startParameter
        )
    }

    def "external variants with equal content are shared across factories"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        resultA.is(resultB)
    }

    def "a cache hit constructs nothing"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()
        def identifier = id("v1")
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(identifier), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def variantB = Mock(ResolvedVariant)

        when:
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variantB, definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        1 * variantB.getIdentifier() >> identifier
        1 * variantB.getArtifacts() >> Stub(ResolvedArtifactSet)
        0 * variantB._
        resultB.is(resultA)
    }

    def "external variants resolving the same source artifact instances are shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()
        def artifact = Mock(ResolvableArtifact)

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1"), artifactSet(artifact)), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1"), artifactSet(artifact)), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        resultA.is(resultB)
    }

    def "external variants resolving different source artifact instances are not shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1"), artifactSet(Mock(ResolvableArtifact))), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1"), artifactSet(Mock(ResolvableArtifact))), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    def "failed external variants are not shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()
        def failed = { new UnavailableResolvedArtifactSet(new RuntimeException("broken")) }

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1"), failed()), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1"), failed()), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    def "external variants differing in step parameter fingerprints are not shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(2))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    def "external variants differing in step from/to attributes are not shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1, attributes("from", "a")))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1, attributes("from", "b")))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    def "external variants differing in target attributes are not shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(attributes("target", "a"), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(attributes("target", "b"), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    def "external variants of different components are not shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "b"), variant(id("v1")), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    def "variants transformed by a chain requiring dependencies are only shared for the same dependencies resolver"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()
        def resolver = dependenciesResolver()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1, true))), resolver)
        def sameResolver = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1, true))), resolver)
        def otherResolver = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1, true))), dependenciesResolver())

        then:
        resultA.is(sameResolver)
        !resultA.is(otherResolver)
    }

    def "ad hoc variants are not cached"() {
        given:
        def factory = newFactory()

        when:
        def resultA = factory.transformedExternalArtifacts(component("g", "a"), variant(null), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factory.transformedExternalArtifacts(component("g", "a"), variant(null), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    def "project variants are cached per factory, not shared"() {
        given:
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedProjectArtifacts(component("g", "a"), variant(id("v1")), projectDefinition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultA2 = factoryA.transformedProjectArtifacts(component("g", "a"), variant(id("v1")), projectDefinition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedProjectArtifacts(component("g", "a"), variant(id("v1")), projectDefinition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        resultA.is(resultA2)
        !resultA.is(resultB)
    }

    def "opt-out property restores per-scope caching of external variants"() {
        given:
        startParameter.setArtifactTransformsPerScopeVariantCache(true)
        def factoryA = newFactory()
        def factoryB = newFactory()

        when:
        def resultA = factoryA.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))
        def resultB = factoryB.transformedExternalArtifacts(component("g", "a"), variant(id("v1")), definition(targetAttributes(), chain(step(1))), Mock(TransformUpstreamDependenciesResolver))

        then:
        !resultA.is(resultB)
    }

    private static ComponentIdentifier component(String group, String module) {
        DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, module), "1.0")
    }

    private static VariantResolveMetadata.Identifier id(String name) {
        new TestVariantIdentifier(name)
    }

    private static ImmutableAttributes targetAttributes() {
        attributes("target", "x")
    }

    private static ImmutableAttributes attributes(String name, String value) {
        AttributeTestUtil.attributesTyped(Collections.singletonMap(Attribute.of(name, String), value))
    }

    private TransformStep step(int hashByte, boolean needsDependencies = false) {
        step(hashByte, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, needsDependencies)
    }

    private TransformStep step(int hashByte, ImmutableAttributes from, ImmutableAttributes to = ImmutableAttributes.EMPTY, boolean needsDependencies = false) {
        def transform = Stub(Transform) {
            getSecondaryInputHash() >> HashCode.fromBytes([0, 0, 0, hashByte] as byte[])
            getFromAttributes() >> from
            getToAttributes() >> to
            requiresDependencies() >> needsDependencies
        }
        new TransformStep(transform, Mock(TransformInvocationFactory), Mock(DomainObjectContext), Mock(InputFingerprinter))
    }

    private static TransformChain chain(TransformStep step) {
        new TransformChain(null, step)
    }

    private VariantDefinition definition(ImmutableAttributes target, TransformChain chain) {
        Stub(VariantDefinition) {
            getTargetAttributes() >> target
            getTransformChain() >> chain
        }
    }

    private VariantDefinition projectDefinition(ImmutableAttributes target, TransformChain chain) {
        Stub(VariantDefinition) {
            getTargetAttributes() >> target
            getTransformChain() >> chain
            getTransformStep() >> chain.last
            getPrevious() >> null
        }
    }

    private ResolvedVariant variant(VariantResolveMetadata.Identifier identifier) {
        variant(identifier, Stub(ResolvedArtifactSet))
    }

    private ResolvedVariant variant(VariantResolveMetadata.Identifier identifier, ResolvedArtifactSet artifacts) {
        Stub(ResolvedVariant) {
            getIdentifier() >> identifier
            getSourceVariantId() >> Stub(VariantIdentifier)
            getArtifacts() >> artifacts
            getCapabilities() >> ImmutableCapabilities.EMPTY
            getAttributes() >> ImmutableAttributes.EMPTY
        }
    }

    private static ResolvedArtifactSet artifactSet(ResolvableArtifact... artifacts) {
        new ResolvedArtifactSet() {
            @Override
            void visit(ResolvedArtifactSet.Visitor visitor) {
            }

            @Override
            void visitTransformSources(ResolvedArtifactSet.TransformSourceVisitor visitor) {
            }

            @Override
            void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
                artifacts.each { visitor.execute(it) }
            }

            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
            }
        }
    }

    // Deliberately not a Spock mock: the cache keys rely on the identity equals/hashCode of resolvers
    private TransformUpstreamDependenciesResolver dependenciesResolver() {
        def dependencies = Stub(TransformUpstreamDependencies)
        new TransformUpstreamDependenciesResolver() {
            @Override
            TransformUpstreamDependencies dependenciesFor(ComponentIdentifier componentId, TransformStep transformStep) {
                dependencies
            }
        }
    }

    private static class TestVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final String name

        TestVariantIdentifier(String name) {
            this.name = name
        }

        @Override
        boolean equals(Object obj) {
            obj instanceof TestVariantIdentifier && obj.name == name
        }

        @Override
        int hashCode() {
            name.hashCode()
        }
    }
}
