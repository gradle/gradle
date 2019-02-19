/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import spock.lang.Specification
import spock.lang.Unroll

class TargetJavaPlatformRulesTest extends Specification {
    private JavaEcosystemSupport.TargetPlatformCompatibilityRules compatibilityRules = new JavaEcosystemSupport.TargetPlatformCompatibilityRules()
    private JavaEcosystemSupport.TargetPlatformDisambiguationRules disambiguationRules = new JavaEcosystemSupport.TargetPlatformDisambiguationRules(8)

    @Unroll("compatibility consumer=#consumer producer=#producer compatible=#compatible")
    def "check compatibility rules"() {
        CompatibilityCheckDetails details = Mock(CompatibilityCheckDetails)

        when:
        compatibilityRules.execute(details)

        then:
        1 * details.getConsumerValue() >> consumer
        1 * details.getProducerValue() >> producer

        if (compatible) {
            1 * details.compatible()
        } else {
            1 * details.incompatible()
        }

        where:
        consumer | producer | compatible
        null     | 5        | true
        null     | 6        | true
        null     | 11       | true

        8        | 6        | true
        8        | 7        | true
        8        | 8        | true
        8        | 9        | false
        8        | 10       | false
        8        | 11       | false
    }

    @Unroll("disamgiguates when consumer=#consumer and candidates=#candidates chooses=#expected")
    def "check disambiguation rules"() {
        MultipleCandidatesDetails details = Mock(MultipleCandidatesDetails)

        when:
        disambiguationRules.execute(details)

        then:
        1 * details.getConsumerValue() >> consumer
        1 * details.getCandidateValues() >> candidates
        1 * details.closestMatch(expected)

        where:
        consumer | candidates | expected
        null     | [4, 8, 11] | 8
        null     | [11, 8]    | 8

        6        | [6]        | 6
        7        | [6, 7]     | 7
        8        | [6, 7]     | 7
        9        | [6, 7, 9]  | 9
        10       | [6, 7, 9]  | 9
        11       | [6, 7, 9]  | 9

    }
}
