/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.deprecation

import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.Unroll

class DeprecatedFeatureUsageTest extends Specification {

    @Unroll
    def "formats messages"() {
        expect:
        new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, documentationReference, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, getClass()).formattedMessage() == expected

        where:
        summary   | removalDetails   | advice   | contextualAdvice   | documentationReference                      | expected
        "summary" | "removalDetails" | null     | null               | DocumentationReference.NO_DOCUMENTATION     | "summary removalDetails"
        "summary" | "removalDetails" | "advice" | null               | DocumentationReference.NO_DOCUMENTATION     | "summary removalDetails advice"
        "summary" | "removalDetails" | "advice" | "contextualAdvice" | DocumentationReference.NO_DOCUMENTATION     | "summary removalDetails contextualAdvice advice"
        "summary" | "removalDetails" | "advice" | "contextualAdvice" | DocumentationReference.create("foo", "bar") | "summary removalDetails contextualAdvice advice See https://docs.gradle.org/${GradleVersion.current().version}/userguide/foo.html#bar for more details."
    }

    def "formats message with documentation reference"() {
        when:
        def documentationReference = DocumentationReference.create("foo", "bar")
        def deprecatedFeatureUsage = new DeprecatedFeatureUsage("summary", "removalDetails", "advice", "contextualAdvice", documentationReference, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, getClass())

        then:
        deprecatedFeatureUsage.formattedMessage() == "summary removalDetails contextualAdvice advice ${documentationReference.consultDocumentationMessage()}"
    }

}
