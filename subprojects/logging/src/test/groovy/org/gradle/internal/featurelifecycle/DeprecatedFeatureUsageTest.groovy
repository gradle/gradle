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

package org.gradle.internal.featurelifecycle

import spock.lang.Specification
import spock.lang.Unroll

class DeprecatedFeatureUsageTest extends Specification {

    @Unroll
    def "formats messages"() {
        expect:
        new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, getClass()).formattedMessage() == expected

        where:
        expected                                         | summary   | removalDetails   | advice   | contextualAdvice
        "summary removalDetails"                         | "summary" | "removalDetails" | null     | null
        "summary removalDetails advice"                  | "summary" | "removalDetails" | "advice" | null
        "summary removalDetails contextualAdvice advice" | "summary" | "removalDetails" | "advice" | "contextualAdvice"
    }

}
