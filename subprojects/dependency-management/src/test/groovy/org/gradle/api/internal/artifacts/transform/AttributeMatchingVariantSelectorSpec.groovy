/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Task
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.Describables
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

class AttributeMatchingVariantSelectorSpec extends Specification {

    def consumerProvidedVariantFinder = Mock(ConsumerProvidedVariantFinder)
    def transformationNodeRegistry = Mock(TransformationNodeRegistry)
    def attributeMatcher = Mock(AttributeMatcher)
    def attributesSchema = Mock(AttributesSchemaInternal) {
        withProducer(_) >> attributeMatcher
    }
    def attributesFactory = Mock(ImmutableAttributesFactory) {
        concat(_, _) >> { args -> return args[0]}
    }
    def requestedAttributes = AttributeTestUtil.attributes(['artifactType' : 'jar'])
    def variantAttributes = AttributeTestUtil.attributes(['artifactType': 'jar'])
    def otherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'classes'])
    def dependenciesResolverFactory = Mock(ExtraExecutionGraphDependenciesResolverFactory)
    def variant = Mock(ResolvedVariant) {
        getAttributes() >> variantAttributes
        asDescribable() >> Describables.of("mock resolved variant")
    }
    def variantSet = Mock(ResolvedVariantSet) {
        asDescribable() >> Describables.of("mock producer")
        getVariants() >> [variant]
    }

    def 'direct match on variant means no finder interaction'() {
        given:
        def resolvedArtifactSet = Mock(ResolvedArtifactSet)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(variantSet)

        then:
        result == resolvedArtifactSet
        1 * attributeMatcher.matches(_, _) >> [variant]
        1 * variant.getArtifacts() >> resolvedArtifactSet
        0 * consumerProvidedVariantFinder._
    }

    def 'multiple match on variant results in ambiguous exception'() {
        given:
        def otherResolvedVariant = Mock(ResolvedVariant) {
            asDescribable() >> Describables.of('other mocked variant')
            getAttributes() >> otherVariantAttributes
        }
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(variantSet)

        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                throw new AssertionError("Expected an exception")
            }

            @Override
            void visitFailure(Throwable failure) {
                assert failure instanceof AmbiguousVariantSelectionException
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> [variant, otherResolvedVariant]
        2 * attributeMatcher.isMatching(_, _, _) >> true
        0 * consumerProvidedVariantFinder._
    }

    def 'selecting a transform results in added DefaultTransformationDependency'() {
        given:
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(variantSet)


        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                assert dependency instanceof DefaultTransformationDependency
            }

            @Override
            void visitFailure(Throwable failure) {
                throw failure
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, Mock(Transformation), 1)
        }
    }

    def 'can disambiguate sub chains'() {
        given:
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant]
        }
        def transform1 = Mock(Transformation)
        def transform2 = Mock(Transformation)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet)


        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                assert dependency instanceof DefaultTransformationDependency
                assert dependency.transformation == transform1
            }

            @Override
            void visitFailure(Throwable failure) {
                throw failure
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> Collections.emptyList()
        1 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 3)
        }
        1 * attributeMatcher.matches(_, _) >> { args -> args[0] }
    }

    def 'can disambiguate 2 equivalent chains by picking shortest'() {
        given:
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant]
        }
        def transform1 = Mock(Transformation)
        def transform2 = Mock(Transformation)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet)


        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                assert dependency instanceof DefaultTransformationDependency
                assert dependency.transformation == transform1
            }

            @Override
            void visitFailure(Throwable failure) {
                throw failure
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> Collections.emptyList()
        1 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 3)
        }
        1 * attributeMatcher.matches(_, _) >> { args -> args[0] }
    }

    def 'can leverage schema disambiguation'() {
        given:
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant]
        }
        def transform1 = Mock(Transformation)
        def transform2 = Mock(Transformation)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet)


        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                assert dependency instanceof DefaultTransformationDependency
                assert dependency.transformation == transform1
            }

            @Override
            void visitFailure(Throwable failure) {
                throw failure
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(otherVariantAttributes, transform1, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(variantAttributes, transform2, 3)
        }
        1 * attributeMatcher.matches(_, _) >> { args -> [args[0].get(0)] }
    }

    def 'can disambiguate between three chains when one subset of both others'() {
        given:
        def yetAnotherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'foo'])
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def yetAnotherVariant = Mock(ResolvedVariant) {
            getAttributes() >> yetAnotherVariantAttributes
            asDescribable() >> Describables.of("mock another resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant, yetAnotherVariant]
        }
        def transform1 = Mock(Transformation)
        def transform2 = Mock(Transformation)
        def transform3 = Mock(Transformation)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet)


        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                assert dependency instanceof DefaultTransformationDependency
                assert dependency.transformation == transform3
            }

            @Override
            void visitFailure(Throwable failure) {
                throw failure
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> Collections.emptyList()
        2 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(yetAnotherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform3, 2)
        }
        1 * attributeMatcher.matches(_, _) >> { args -> args[0] }
    }

    def 'can disambiguate 3 equivalent chains by picking shortest'() {
        given:
        def yetAnotherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'foo'])
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def yetAnotherVariant = Mock(ResolvedVariant) {
            getAttributes() >> yetAnotherVariantAttributes
            asDescribable() >> Describables.of("mock another resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant, yetAnotherVariant]
        }
        def transform1 = Mock(Transformation)
        def transform2 = Mock(Transformation)
        def transform3 = Mock(Transformation)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet)


        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                assert dependency instanceof DefaultTransformationDependency
                assert dependency.transformation == transform2
            }

            @Override
            void visitFailure(Throwable failure) {
                throw failure
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(yetAnotherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform3, 3)
        }
        2 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * attributeMatcher.matches(_, _) >> { args -> args[0] }
    }

    def 'cannot disambiguate 3 chains when 2 different'() {
        given:
        def yetAnotherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'foo'])
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def yetAnotherVariant = Mock(ResolvedVariant) {
            getAttributes() >> yetAnotherVariantAttributes
            asDescribable() >> Describables.of("mock another resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant, yetAnotherVariant]
        }
        def transform1 = Mock(Transformation)
        def transform2 = Mock(Transformation)
        def transform3 = Mock(Transformation)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformationNodeRegistry, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet)


        then:
        result.visitDependencies(new TaskDependencyResolveContext() {
            @Override
            void add(Object dependency) {
                throw new AssertionError("Expected an exception")
            }

            @Override
            void visitFailure(Throwable failure) {
                assert failure instanceof AmbiguousTransformException
            }

            @Override
            Task getTask() {
                return null
            }
        })
        1 * attributeMatcher.matches(_, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(yetAnotherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform3, 3)
        }
        1 * attributeMatcher.matches(_, _) >> { args -> args[0] }
        3 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >>> [false, false, true]
    }

    static ConsumerVariantMatchResult match(ImmutableAttributes output, Transformation trn, int depth) {
        def result = new ConsumerVariantMatchResult(2)
        result.matched(output, trn, depth)
        result
    }
}
