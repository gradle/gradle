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
import org.gradle.api.attributes.Bundling
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.attributes.Bundling.EXTERNAL
import static org.gradle.api.attributes.Bundling.EMBEDDED
import static org.gradle.api.attributes.Bundling.SHADOWED

class BundlingRulesTest extends Specification {
    private JavaEcosystemSupport.BundlingCompatibilityRules compatibilityRules = new JavaEcosystemSupport.BundlingCompatibilityRules()
    private JavaEcosystemSupport.BundlingDisambiguationRules disambiguationRules = new JavaEcosystemSupport.BundlingDisambiguationRules()

    @Unroll("compatibility consumer=#consumer producer=#producer compatible=#compatible")
    def "check compatibility rules"() {
        CompatibilityCheckDetails details = Mock(CompatibilityCheckDetails)

        when:
        compatibilityRules.execute(details)

        then:
        1 * details.getConsumerValue() >> bundling(consumer)
        1 * details.getProducerValue() >> bundling(producer)

        if (compatible && !(consumer == producer)) {
            1 * details.compatible()
        } else {
            0 * _
        }

        where:
        consumer | producer | compatible
        null     | EXTERNAL | true
        null     | EMBEDDED | true
        null     | SHADOWED | true

        EXTERNAL | EXTERNAL | true
        EXTERNAL | EMBEDDED | true
        EXTERNAL | SHADOWED | true

        EMBEDDED | EXTERNAL | false
        EMBEDDED | EMBEDDED | true
        EMBEDDED | SHADOWED | true

        SHADOWED | EXTERNAL | false
        SHADOWED | EMBEDDED | false
        SHADOWED | SHADOWED | true

    }

    @Unroll("disamgiguates when consumer=#consumer and candidates=#candidates chooses=#expected")
    def "check disambiguation rules"() {
        MultipleCandidatesDetails details = Mock(MultipleCandidatesDetails)

        when:
        disambiguationRules.execute(details)

        then:
        1 * details.getConsumerValue() >> bundling(consumer)
        1 * details.getCandidateValues() >> candidates.collect { bundling(it) }
        1 * details.closestMatch({ it.name == expected })

        where:
        consumer | candidates                     | expected
        null     | [EXTERNAL, EMBEDDED, SHADOWED] | EXTERNAL
        null     | [EXTERNAL, EMBEDDED]           | EXTERNAL
        null     | [EXTERNAL, SHADOWED]           | EXTERNAL
        null     | [EMBEDDED, SHADOWED]           | EMBEDDED

        EXTERNAL | [EXTERNAL, EMBEDDED, SHADOWED] | EXTERNAL
        EXTERNAL | [EXTERNAL, EMBEDDED]           | EXTERNAL
        EXTERNAL | [EXTERNAL, SHADOWED]           | EXTERNAL
        EXTERNAL | [EMBEDDED, SHADOWED]           | EMBEDDED

        EMBEDDED | [EMBEDDED, SHADOWED]           | EMBEDDED

    }

    private Bundling bundling(String name) {
        if (name == null) {
            null
        } else {
            TestUtil.objectFactory().named(Bundling, name)
        }
    }
}
