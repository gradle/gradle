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

import org.gradle.api.internal.DomainObjectContext
import org.gradle.internal.execution.fingerprint.InputFingerprinter
import spock.lang.Specification

class TransformationMatchingSpec extends Specification {

    def "different TransformationStep does not contain each other"() {
        given:
        def step1 = step()
        def step2 = step()

        expect:
        !step1.endsWith(step2)
        !step2.endsWith(step1)
    }

    def "TransformationStep contains itself"() {
        given:
        def step = step()

        expect:
        step.endsWith(step)
    }

    def "chain contains its final step"() {
        given:
        def step1 = step()
        def step2 = step()
        def chain = new TransformationChain(step1, step2)

        expect:
        chain.endsWith(step2)
        !chain.endsWith(step1)
        !step1.endsWith(chain)
        !step2.endsWith(chain)

    }

    def "chain contains itself"() {
        given:
        def step1 = step()
        def step2 = step()
        def chain = new TransformationChain(step1, step2)

        expect:
        chain.endsWith(chain)
    }

    def "longer chain contains shorter chain"() {
        given:
        def step1 = step()
        def step2 = step()
        def step3 = step()
        def subChain = new TransformationChain(step2, step3)
        def longChain = new TransformationChain(new TransformationChain(step1, step2), step3)

        expect:
        longChain.endsWith(subChain)
        !subChain.endsWith(longChain)
    }

    def "different chains do not contain each other"() {
        given:
        def step1 = step()
        def step2 = step()
        def step3 = step()
        def chain1 = new TransformationChain(step2, step3)
        def chain2 = new TransformationChain(step1, step2)
        def chain3 = new TransformationChain(step1, step3)

        expect:
        !chain1.endsWith(chain2)
        !chain2.endsWith(chain1)
        !chain3.endsWith(chain2)
        !chain3.endsWith(chain1)
        !chain2.endsWith(chain3)
        !chain1.endsWith(chain3)
    }

    private TransformationStep step() {
        new TransformationStep(Mock(Transformer), Mock(TransformerInvocationFactory), Mock(DomainObjectContext), Mock(InputFingerprinter))
    }
}
