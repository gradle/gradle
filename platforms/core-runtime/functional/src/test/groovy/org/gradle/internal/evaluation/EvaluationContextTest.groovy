/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.evaluation

import spock.lang.Specification

import java.util.function.BiFunction

class EvaluationContextTest extends Specification {
    def owner = createOwner()

    def "can run evaluation of #evaluator in scope"() {
        given:
        def evaluation = Mock(TestEvaluation)

        when:
        def result = evaluator.apply(owner, evaluation)

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
        def result1 = evaluator.apply(owner, evaluation)
        def result2 = evaluator.apply(owner, evaluation)

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
        def otherOwner = createOwner()

        when:
        def result = evaluator.apply(owner) {
            evaluator.apply(otherOwner, evaluation)
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
        def result = context().tryEvaluate(owner, "fallback", evaluation)

        then:
        1 * evaluation.evaluate() >> "result"
        result == "result"
    }

    def "fallback value is used when circular evaluation detected"() {
        given:
        def evaluation = Mock(TestEvaluation)

        when:
        def result = context().evaluate(owner) {
            context().tryEvaluate(this.owner, "fallback", evaluation)
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

    def "re-evaluating the owner in #evaluator throws exception"() {
        when:
        evaluator.apply(owner) {
            evaluator.apply(this.owner) {}
        }

        then:
        CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [owner, owner]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "re-evaluating the owner in #evaluator1 then #evaluator2 throws exception"() {
        when:
        evaluator1.apply(owner) {
            evaluator2.apply(this.owner) {}
        }

        then:
        CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [owner, owner]

        where:
        evaluator1         | evaluator2
        evaluateInBlock()  | evaluateInLambda()
        evaluateInLambda() | evaluateInBlock()
    }

    def "re-evaluating the owner in #evaluator throws exception when intermediate owner is involved"() {
        def otherOwner = createOwner()

        when:
        evaluator.apply(owner) {
            evaluator.apply(otherOwner) {
                evaluator.apply(this.owner) {}
            }
        }

        then:
        CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [owner, otherOwner, owner]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "only circular part of the chain is reported when evaluating in #evaluator"() {
        def otherOwner = createOwner()

        when:
        evaluator.apply(otherOwner) {
            evaluator.apply(this.owner) {
                evaluator.apply(this.owner) {}
            }
        }

        then:
        CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [owner, owner]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can evaluate owner evaluating with #evaluator again in nested block"() {
        given:
        def evaluation = Mock(TestEvaluation)
        when:
        def result = evaluator.apply(owner) {
            context().evaluateNested {
                evaluator.apply(this.owner, evaluation)
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
        evaluator.apply(owner) {
            context().evaluateNested {}

            // The context is restored, and re-evaluating the owner is now an error again
            evaluator.apply(this.owner) {}
        }

        then:
        CircularEvaluationException ex = thrown()
        ex.evaluationCycle == [owner, owner]

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can handle owner with broken equals in #evaluator"() {
        given:
        owner.equals(_) >> { false }

        when:
        evaluator.apply(owner) {
            evaluator.apply(this.owner) {}
        }

        then:
        CircularEvaluationException ex = thrown()
        ex.evaluationCycle.size() == 2
        // Have to use reference check because owner's equals is broken
        ex.evaluationCycle[0] === owner
        ex.evaluationCycle[1] === owner

        where:
        evaluator << [evaluateInLambda(), evaluateInBlock()]
    }

    def "can handle owner with broken equals in tryEvaluate"() {
        given:
        owner.equals(_) >> { false }

        when:
        def result = context().evaluate(owner) {
            context().tryEvaluate(this.owner, "fallback") { "result" }
        }

        then:
        result == "fallback"
    }

    BiFunction<EvaluationOwner, TestEvaluation, String> evaluateInLambda() {
        return new BiFunction<EvaluationOwner, TestEvaluation, String>() {
            @Override
            String apply(EvaluationOwner owner, TestEvaluation evaluation) {
                context().evaluate(owner, evaluation)
            }

            @Override
            String toString() {
                "lambda"
            }
        }
    }

    BiFunction<EvaluationOwner, TestEvaluation, String> evaluateInBlock() {
        return new BiFunction<EvaluationOwner, TestEvaluation, String>() {
            @Override
            @SuppressWarnings('GroovyUnusedAssignment')
            String apply(EvaluationOwner owner, TestEvaluation evaluation) {
                try (def scope = context().open(owner)) {
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

    EvaluationOwner createOwner() {
        return Mock(EvaluationOwner)
    }

    static interface TestEvaluation extends ScopedEvaluation<String, RuntimeException> {}
}
