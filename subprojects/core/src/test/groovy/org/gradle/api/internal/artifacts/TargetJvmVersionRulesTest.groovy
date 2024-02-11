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

import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.attributes.CompatibilityCheckResult
import org.gradle.api.internal.attributes.CompatibilityRule
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DisambiguationRule
import org.gradle.api.internal.attributes.MultipleCandidatesResult
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class TargetJvmVersionRulesTest extends Specification {
    private CompatibilityRule<Object> compatibilityRules
    private DisambiguationRule<Object> disambiguationRules

    def setup() {
        AttributesSchema schema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
        JavaEcosystemSupport.configureSchema(schema, TestUtil.objectFactory())
        compatibilityRules = schema.compatibilityRules(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
        disambiguationRules = schema.disambiguationRules(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
    }

    @Unroll("compatibility consumer=#consumer producer=#producer compatible=#compatible")
    def "check compatibility rules"() {
        CompatibilityCheckResult details = Mock(CompatibilityCheckResult)

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
        8        | 6        | true
        8        | 7        | true
        8        | 8        | true
        8        | 9        | false
        8        | 10       | false
        8        | 11       | false
    }

    @Unroll("disamgiguates when consumer=#consumer and candidates=#candidates chooses=#expected")
    def "check disambiguation rules"() {
        MultipleCandidatesResult details = Mock()

        when:
        disambiguationRules.execute(details)

        then:
        1 * details.getCandidateValues() >> candidates
        1 * details.closestMatch(expected)
        1 * details.hasResult()
        0 * details._

        where:
        consumer | candidates | expected
        6        | [6]        | 6
        7        | [6, 7]     | 7
        8        | [6, 7]     | 7
        9        | [6, 7, 9]  | 9
        10       | [6, 7, 9]  | 9
        11       | [6, 7, 9]  | 9

    }
}
