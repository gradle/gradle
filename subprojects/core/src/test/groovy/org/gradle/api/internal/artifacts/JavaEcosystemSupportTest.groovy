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

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.internal.component.model.DefaultMultipleCandidateResult
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.api.attributes.Bundling.EMBEDDED
import static org.gradle.api.attributes.Bundling.EXTERNAL
import static org.gradle.api.attributes.Bundling.SHADOWED

class JavaEcosystemSupportTest extends Specification {
    def "check usage compatibility rules (consumer value=#consumer, producer value=#producer, compatible=#compatible)"() {
        given:
        JavaEcosystemSupport.UsageCompatibilityRules rules = new JavaEcosystemSupport.UsageCompatibilityRules()
        def details = Mock(CompatibilityCheckDetails)
        when:
        rules.execute(details)

        then:
        1 * details.getConsumerValue() >> usage(consumer)
        1 * details.getProducerValue() >> usage(producer)
        if (producer == consumer) {
            // implementations are NOT required to say "compatible" because
            // they should not even be called in this case
            0 * details._()
        } else if (compatible) {
            1 * details.compatible()
        } else {
            0 * details._()
        }

        where:
        consumer                     | producer                                             | compatible
        null                         | Usage.JAVA_API                                       | true
        null                         | Usage.JAVA_RUNTIME                                   | true

        Usage.JAVA_API               | Usage.JAVA_API                                       | true
        Usage.JAVA_API               | Usage.JAVA_RUNTIME                                   | true

        Usage.JAVA_RUNTIME           | Usage.JAVA_API                                       | false
        Usage.JAVA_RUNTIME           | Usage.JAVA_RUNTIME                                   | true

        // Temporary compatibility
        Usage.JAVA_API               | JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS      | true
        Usage.JAVA_RUNTIME           | JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS      | true
    }

    @Issue("gradle/gradle#8700")
    def "check usage disambiguation rules (consumer=#consumer, candidates=#candidates, selected=#preferred)"() {
        given:
        JavaEcosystemSupport.UsageDisambiguationRules rules = new JavaEcosystemSupport.UsageDisambiguationRules(
            usage(Usage.JAVA_API),
            usage(JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS),
            usage(Usage.JAVA_RUNTIME),
            usage(JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS)
        )
        MultipleCandidatesDetails details = new DefaultMultipleCandidateResult(usage(consumer), candidates.collect { usage(it)} as Set)

        when:
        rules.execute(details)

        then:
        details.hasResult()
        !details.matches.empty
        details.matches == [usage(preferred)] as Set

        details

        where: // not exhaustive, tests pathological cases
        consumer                | candidates                                                                                                        | preferred
        Usage.JAVA_API          | [Usage.JAVA_API, Usage.JAVA_RUNTIME]                                                                              | Usage.JAVA_API
        Usage.JAVA_RUNTIME      | [Usage.JAVA_RUNTIME, Usage.JAVA_API]                                                                              | Usage.JAVA_RUNTIME

        //Temporary compatibility
        Usage.JAVA_API          | [JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS, JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS]                | JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS
        Usage.JAVA_RUNTIME      | [Usage.JAVA_API, JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS]                                               | JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS

        // while unlikely that a candidate would expose both JAVA_API_JARS and JAVA_API,
        // this confirms that JAVA_API_JARS takes precedence, per JavaEcosystemSupport
        Usage.JAVA_API          | [JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS, Usage.JAVA_API, JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS] | JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS
    }

    def "check bundling compatibility rules consumer=#consumer producer=#producer compatible=#compatible"() {
        CompatibilityCheckDetails details = Mock(CompatibilityCheckDetails)

        when:
        new JavaEcosystemSupport.BundlingCompatibilityRules().execute(details)

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

    def "check bundling disambiguation rules consumer=#consumer and candidates=#candidates chooses=#expected"() {
        MultipleCandidatesDetails details = Mock(MultipleCandidatesDetails)

        when:
        new JavaEcosystemSupport.BundlingDisambiguationRules().execute(details)

        then:
        1 * details.getConsumerValue() >> bundling(consumer)
        1 * details.getCandidateValues() >> candidates.collect { bundling(it) }
        1 * details.closestMatch({ assert it.name == expected })

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

    private Usage usage(String value) {
        if (value == null) {
            null
        } else {
            TestUtil.objectFactory().named(Usage, value)
        }
    }

    private Bundling bundling(String name) {
        if (name == null) {
            null
        } else {
            TestUtil.objectFactory().named(Bundling, name)
        }
    }
}
