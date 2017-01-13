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
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit
import spock.lang.Specification

class JacocoViolationRuleImplTest extends Specification {

    JacocoViolationRuleImpl rule = new JacocoViolationRuleImpl()

    def "provides expected default field values"() {
        expect:
        rule.enabled
        rule.element == 'BUNDLE'
        rule.includes == ['*']
        rule.excludes == []
    }

    def "can add limits"() {
        when:
        def limit = rule.limit(new Action<JacocoLimit>() {
            @Override
            void execute(JacocoLimit jacocoLimit) {
                jacocoLimit.with {
                    counter = 'CLASS'
                    value = 'TOTALCOUNT'
                    minimum = 0.0
                    maximum = 1.0
                }
            }
        })

        then:
        rule.limits.size() == 1
        rule.limits[0] == limit

        when:
        limit = rule.limit(new Action<JacocoLimit>() {
            @Override
            void execute(JacocoLimit jacocoLimit) {
                jacocoLimit.with {
                    counter = 'COMPLEXITY'
                    value = 'MISSEDCOUNT'
                    minimum = 0.2
                    maximum = 0.6
                }
            }
        })

        then:
        rule.limits.size() == 2
        rule.limits[1] == limit
    }

    def "returned includes, excludes and limits are unmodifiable"() {
        when:
        rule.includes << ['*']

        then:
        thrown(UnsupportedOperationException)

        when:
        rule.excludes << ['*']

        then:
        thrown(UnsupportedOperationException)

        when:
        rule.limits << new JacocoLimitImpl()

        then:
        thrown(UnsupportedOperationException)
    }
}
