/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.registry

import org.gradle.internal.Factory
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.reflect.MethodDescription
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.core.ModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelView
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.inspect.DefaultMethodModelRuleExtractionContext
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.DefaultModelRuleInvoker
import org.gradle.model.internal.inspect.ExtractedModelRule
import org.gradle.model.internal.inspect.FormattingValidationProblemCollector
import org.gradle.model.internal.inspect.MethodModelRuleApplicationContext
import org.gradle.model.internal.inspect.MethodRuleAction
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType

import java.lang.annotation.Annotation
import java.lang.reflect.Method

public abstract class AbstractAnnotationModelRuleExtractorTest extends ProjectRegistrySpec {
    def ruleDefinition = Mock(MethodRuleDefinition)

    protected abstract AbstractAnnotationDrivenComponentModelRuleExtractor getRuleHandler();

    abstract Class<? extends Annotation> getAnnotation();

    abstract Class<?> getRuleClass();

    def "handles methods annotated with @#annotationName"() {
        when:
        1 * ruleDefinition.isAnnotationPresent(annotation) >> false

        then:
        !ruleHandler.isSatisfiedBy(ruleDefinition)

        when:
        1 * ruleDefinition.isAnnotationPresent(annotation) >> true

        then:
        ruleHandler.isSatisfiedBy(ruleDefinition)

        where:
        annotationName << [annotation.getSimpleName()]
    }

    void apply(ExtractedModelRule rule, ModelRegistry registry) {
        def context = Stub(MethodModelRuleApplicationContext) {
            getRegistry() >> registry
            contextualize(_) >> { MethodRuleAction action ->
                Stub(ModelAction) {
                    getSubject() >> action.subject
                    getInputs() >> action.inputs
                    execute(_, _) >> { MutableModelNode node, List<ModelView<?>> inputs ->
                        action.execute(new DefaultModelRuleInvoker(rule.ruleDefinition.method, { JavaReflectionUtil.newInstance(rule.ruleDefinition.method.method.declaringClass) } as Factory), node, inputs) }
                }
            }
            getScope() >> ModelPath.ROOT
        }
        def node = Stub(MutableModelNode)
        rule.apply(context, node)
    }

    void apply(MethodRuleDefinition<?, ?> definition) {
        def rule = extract(definition)
        def registryNode = Stub(MutableModelNode) {
            isAtLeast(_) >> true
            asMutable(_, _) >> { ModelType type, ModelRuleDescriptor ruleDescriptor ->
                return Stub(ModelView) {
                    getInstance() >> { Stub(type.concreteClass) }
                }
            }
            asImmutable(_, _) >> { ModelType type, ModelRuleDescriptor ruleDescriptor ->
                return Stub(ModelView) {
                    getInstance() >> { Stub(type.concreteClass) }
                }
            }
        }
        def registry = Stub(ModelRegistry) {
            configure(_, _) >> { ModelActionRole role, ModelAction action ->
                action.execute(registryNode, [])
            }
        }
        apply(rule, registry)
    }

    ExtractedModelRule extract(MethodRuleDefinition<?, ?> definition) {
        def problems = new FormattingValidationProblemCollector("rule source", ModelType.of(ruleClass))
        def context = new DefaultMethodModelRuleExtractionContext(null, problems)
        def registration = ruleHandler.registration(definition, context)
        if (context.hasProblems()) {
            throw new InvalidModelRuleDeclarationException(problems.format())
        }
        return registration
    }

    MethodRuleDefinition<?, ?> ruleDefinitionForMethod(String methodName) {
        for (Method candidate : ruleClass.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return DefaultMethodRuleDefinition.create(ruleClass, candidate)
            }
        }
        throw new IllegalArgumentException("Not a test method name")
    }

    String getStringDescription(MethodRuleDefinition ruleDefinition) {
        def builder = new StringBuilder()
        ruleDefinition.descriptor.describeTo(builder)
        builder.toString()
    }

    String getStringDescription(WeaklyTypeReferencingMethod<?, ?> method) {
        return MethodDescription.name(method.getName())
                .takes(method.method.getGenericParameterTypes())
                .toString();
    }
}
