/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jacoco.rules

import org.gradle.api.Action
import org.gradle.testing.jacoco.tasks.rules.JacocoThreshold
import spock.lang.Specification

class JacocoViolationRuleImplTest extends Specification {

    JacocoViolationRuleImpl rule = new JacocoViolationRuleImpl()

    def "provides expected default field values"() {
        expect:
        rule.enabled
        rule.scope == 'BUNDLE'
        rule.includes == ['*']
        rule.excludes == []
    }

    def "can add thresholds"() {
        when:
        def threshold = rule.threshold {
            metric = 'CLASS'
            type = 'TOTALCOUNT'
            minimum = 0.0
            maximum = 1.0
        }

        then:
        rule.thresholds.size() == 1
        rule.thresholds[0] == threshold

        when:
        threshold = rule.threshold(new Action<JacocoThreshold>() {
            @Override
            void execute(JacocoThreshold jacocoThreshold) {
                jacocoThreshold.with {
                    metric = 'COMPLEXITY'
                    type = 'MISSEDCOUNT'
                    minimum = 0.2
                    maximum = 0.6
                }
            }
        })

        then:
        rule.thresholds.size() == 2
        rule.thresholds[1] == threshold
    }

    def "returned includes, excludes and thresholds are unmodifiable"() {
        when:
        rule.includes << ['*']

        then:
        thrown(UnsupportedOperationException)

        when:
        rule.excludes << ['*']

        then:
        thrown(UnsupportedOperationException)

        when:
        rule.thresholds << new JacocoThresholdImpl()

        then:
        thrown(UnsupportedOperationException)
    }
}
