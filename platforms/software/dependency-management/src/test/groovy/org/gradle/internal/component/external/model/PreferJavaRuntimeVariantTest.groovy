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

package org.gradle.internal.component.external.model

import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.MultipleCandidatesResult
import org.gradle.util.TestUtil
import spock.lang.Specification

class PreferJavaRuntimeVariantTest extends Specification {

    final PreferJavaRuntimeVariant schema = new PreferJavaRuntimeVariant(TestUtil.objectInstantiator())

    def "should prefer the runtime variant if the consumer doesn't express any preference and that runtime is in the candidates"() {
        given:
        def rule = schema.disambiguationRules(Usage.USAGE_ATTRIBUTE)
        MultipleCandidatesResult<Usage> candidates = Mock(MultipleCandidatesResult)
        candidates.getConsumerValue() >> consumerValue
        candidates.getCandidateValues() >> candidateValues.collect { usage(it) }

        when:
        rule.execute(candidates)

        then:
        count * candidates.closestMatch({ it.name == Usage.JAVA_RUNTIME })

        where:
        consumerValue      | candidateValues                                 | choosesRuntime
        null               | [Usage.JAVA_API]                                | false
        null               | [Usage.JAVA_RUNTIME]                            | true
        null               | [Usage.JAVA_RUNTIME, Usage.JAVA_API]            | true
        null               | [Usage.JAVA_API, Usage.JAVA_RUNTIME]            | true
        null               | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | true
        null               | [Usage.JAVA_API, "unknown"]                     | false
        Usage.JAVA_API     | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | false
        Usage.JAVA_RUNTIME | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | false
        "unknown"          | [Usage.JAVA_API, "unknown", Usage.JAVA_RUNTIME] | false

        count = choosesRuntime ? 1 : 0
    }

    private static Usage usage(String name) {
        TestUtil.objectFactory().named(Usage, name)
    }
}
