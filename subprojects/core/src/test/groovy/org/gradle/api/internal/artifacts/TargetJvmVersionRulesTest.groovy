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

import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.attributes.AttributeDescriberRegistry
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class TargetJvmVersionRulesTest extends Specification {

    def schema = new DefaultAttributeSelectionSchema(
        AttributeTestUtil.immutableSchema {
            JavaEcosystemSupport.configureServices(it, Mock(AttributeDescriberRegistry), TestUtil.objectFactory())
        }
    )

    @Unroll("compatibility consumer=#consumer producer=#producer compatible=#compatible")
    def "check compatibility rules"() {
        when:
        def matches = schema.matchValue(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            consumer,
            producer
        )

        then:
        matches == compatible

        where:
        consumer | producer | compatible
        8        | 6        | true
        8        | 7        | true
        8        | 8        | true
        8        | 9        | false
        8        | 10       | false
        8        | 11       | false
    }

    @Unroll("disambiguates when consumer=#consumer and candidates=#candidates chooses=#expected")
    def "check disambiguation rules"() {
        when:
        def result = schema.disambiguate(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            consumer,
            candidates as Set
        )

        then:
        result == ([expected] as Set)

        where:
        consumer | candidates | expected
        7        | [6, 7]     | 7
        8        | [6, 7]     | 7
        9        | [6, 7, 9]  | 9
        10       | [6, 7, 9]  | 9
        11       | [6, 7, 9]  | 9

    }
}
