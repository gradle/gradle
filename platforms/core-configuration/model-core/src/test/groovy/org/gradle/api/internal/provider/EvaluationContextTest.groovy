/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider

import spock.lang.Specification

import java.util.function.BiFunction

class EvaluationContextTest extends Specification {
    def provider = createProvider()

    def "can run evaluation of #evaluator in scope"() {
        given:
        def evaluation = Mock(TestEvaluation)

        when:
        def result = evaluator.apply(provider, evaluation)

        then:
        1 * evaluation.evaluate() >> "result"
        result == "result"

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can run evaluation of #evaluator multiple times sequentially"() {
        given:
        def evaluation = Mock(TestEvaluation)

        when:
        def result1 = evaluator.apply(provider, evaluation)
        def result2 = evaluator.apply(provider, evaluation)

        then:
        2 * evaluation.evaluate() >>> ["result1", "result2"]
        result1 == "result1"
        result2 == "result2"

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can run evaluation of #evaluator inside other evaluation"() {
        given:
        def evaluation = Mock(TestEvaluation)
        def otherProvider = createProvider()

        when:
        def result = evaluator.apply(provider) {
            evaluator.apply(otherProvider, evaluation)
        }

        then:
        1 * evaluation.evaluate() >> "result"
        result == "result"

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can run evaluation in scope with fallback"() {
        given:
        def evaluation = Mock(TestEvaluation)

        when:
        def result = context().tryEvaluate(provider, "fallback", evaluation)

        then:
        1 * evaluation.evaluate() >> "result"
        result == "result"
    }

    def "fallback value is used when circular evaluation detected"() {
        given:
        def evaluation = Mock(TestEvaluation)

        when:
        def result = context().evaluate(provider) {
            context().tryEvaluate(provider, "fallback", evaluation)
        }

        then:
        0 * evaluation.evaluate()
        result == "fallback"
    }

    def "can run evaluation in nested scope"() {
        given:
        def evaluation = Mock(TestEvaluation)

        when:
        def result = context().evaluateNested(evaluation)

        then:
        1 * evaluation.evaluate() >> "result"
        result == "result"
    }

    def "re-evaluating the provider in #evaluator throws exception"() {
        when:
        evaluator.apply(provider) {
            evaluator.apply(provider) {}
        }

        then:
        EvaluationContext.CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [provider, provider]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "re-evaluating the provider in #evaluator1 then #evaluator2 throws exception"() {
        when:
        evaluator1.apply(provider) {
            evaluator2.apply(provider) {}
        }

        then:
        EvaluationContext.CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [provider, provider]

        where:
        evaluator1         | evaluator2
        evaluateInBlock()  | evaluateInLambda()
        evaluateInLambda() | evaluateInBlock()
    }

    def "re-evaluating the provider in #evaluator throws exception when intermediate provider is involved"() {
        def otherProvider = createProvider()

        when:
        evaluator.apply(provider) {
            evaluator.apply(otherProvider) {
                evaluator.apply(provider) {}
            }
        }

        then:
        EvaluationContext.CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [provider, otherProvider, provider]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "only circular part of the chain is reported when evaluating in #evaluator"() {
        def otherProvider = createProvider()

        when:
        evaluator.apply(otherProvider) {
            evaluator.apply(provider) {
                evaluator.apply(provider) {}
            }
        }

        then:
        EvaluationContext.CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [provider, provider]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can evaluate provider evaluating with #evaluator again in nested block"() {
        given:
        def evaluation = Mock(TestEvaluation)
        when:
        def result = evaluator.apply(provider) {
            context().evaluateNested {
                evaluator.apply(provider, evaluation)
            }
        }

        then:
        1 * evaluation.evaluate() >> "result"
        result == "result"

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "throws exception when re-evaluating in #evaluator after nested block"() {
        when:
        evaluator.apply(provider) {
            context().evaluateNested {}

            // The context is restored, and re-evaluating the provider is now an error again
            evaluator.apply(provider) {}
        }

        then:
        EvaluationContext.CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [provider, provider]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can handle provider with broken equals in #evaluator"() {
        given:
        provider.equals(_) >> { false }

        when:
        evaluator.apply(provider) {
            evaluator.apply(provider) {}
        }

        then:
        EvaluationContext.CircularEvaluationException ex = thrown()
        ex.evaluationCycle.size() == 2
        // Have to use reference check because provider's equals is broken
        ex.evaluationCycle[0] === provider
        ex.evaluationCycle[1] === provider

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can handle provider with broken equals in tryEvaluate"() {
        given:
        provider.equals(_) >> { false }

        when:
        def result = context().evaluate(provider) {
            context().tryEvaluate(provider, "fallback") { "result" }
        }

        then:
        result == "fallback"
    }

    BiFunction<ProviderInternal<?>, TestEvaluation, String> evaluateInLambda() {
        return new BiFunction<ProviderInternal<?>, TestEvaluation, String>() {
            @Override
            String apply(ProviderInternal<?> provider, TestEvaluation evaluation) {
                context().evaluate(provider, evaluation)
            }

            @Override
            String toString() {
                "lambda"
            }
        }
    }

    BiFunction<ProviderInternal<?>, TestEvaluation, String> evaluateInBlock() {
        return new BiFunction<ProviderInternal<?>, TestEvaluation, String>() {
            @Override
            @SuppressWarnings('GroovyUnusedAssignment')
            String apply(ProviderInternal<?> provider, TestEvaluation evaluation) {
                try (def scope = context().enter(provider)) {
                    return evaluation.evaluate()
                }
            }

            @Override
            String toString() {
                "block"
            }
        }
    }

    EvaluationContext context() {
        return EvaluationContext.current()
    }

    ProviderInternal<?> createProvider() {
        return Mock(ProviderInternal)
    }

    static interface TestEvaluation extends EvaluationContext.ScopedEvaluation<String, RuntimeException> {}
}
