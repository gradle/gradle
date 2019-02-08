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
import org.gradle.api.attributes.java.DependencyPacking
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.attributes.java.DependencyPacking.EXTERNAL
import static org.gradle.api.attributes.java.DependencyPacking.FATJAR
import static org.gradle.api.attributes.java.DependencyPacking.SHADOWED

class DependencyPackingRulesTest extends Specification {
    private JavaEcosystemSupport.PackingCompatibilityRules compatibilityRules = new JavaEcosystemSupport.PackingCompatibilityRules()
    private JavaEcosystemSupport.PackingDisambiguationRules disambiguationRules = new JavaEcosystemSupport.PackingDisambiguationRules()

    @Unroll("compatibility consumer=#consumer producer=#producer compatible=#compatible")
    def "check compatibility rules"() {
        CompatibilityCheckDetails details = Mock(CompatibilityCheckDetails)

        when:
        compatibilityRules.execute(details)

        then:
        1 * details.getConsumerValue() >> packing(consumer)
        1 * details.getProducerValue() >> packing(producer)

        if (compatible && !(consumer == producer)) {
            1 * details.compatible()
        } else {
            0 * _
        }

        where:
        consumer | producer | compatible
        null     | EXTERNAL | true
        null     | FATJAR   | true
        null     | SHADOWED | true

        EXTERNAL | EXTERNAL | true
        EXTERNAL | FATJAR   | true
        EXTERNAL | SHADOWED | true

        FATJAR   | EXTERNAL | false
        FATJAR   | FATJAR   | true
        FATJAR   | SHADOWED | true

        SHADOWED | EXTERNAL | false
        SHADOWED | FATJAR   | false
        SHADOWED | SHADOWED | true

    }

    @Unroll("disamgiguates when consumer=#consumer and candidates=#candidates chooses=#expected")
    def "check disambiguation rules"() {
        MultipleCandidatesDetails details = Mock(MultipleCandidatesDetails)

        when:
        disambiguationRules.execute(details)

        then:
        1 * details.getConsumerValue() >> packing(consumer)
        1 * details.getCandidateValues() >> candidates.collect { packing(it) }
        1 * details.closestMatch({ it.name == expected })

        where:
        consumer | candidates                   | expected
        null     | [EXTERNAL, FATJAR, SHADOWED] | EXTERNAL
        null     | [EXTERNAL, FATJAR]           | EXTERNAL
        null     | [EXTERNAL, SHADOWED]         | EXTERNAL
        null     | [FATJAR, SHADOWED]           | FATJAR

        EXTERNAL | [EXTERNAL, FATJAR, SHADOWED] | EXTERNAL
        EXTERNAL | [EXTERNAL, FATJAR]           | EXTERNAL
        EXTERNAL | [EXTERNAL, SHADOWED]         | EXTERNAL
        EXTERNAL | [FATJAR, SHADOWED]           | FATJAR

        FATJAR   | [FATJAR, SHADOWED]           | FATJAR

    }

    private DependencyPacking packing(String name) {
        if (name == null) {
            null
        } else {
            TestUtil.objectFactory().named(DependencyPacking, name)
        }
    }
}
