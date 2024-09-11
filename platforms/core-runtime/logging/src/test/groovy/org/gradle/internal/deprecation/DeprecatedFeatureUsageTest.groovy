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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.util.GradleVersion
import spock.lang.Specification

class DeprecatedFeatureUsageTest extends Specification {

    def "formats messages"() {
        given:
        def featureUsage = new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, documentationReference, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, "id display name", "id", getClass())

        expect:
        featureUsage.formattedMessage() == expected

        where:
        summary   | removalDetails   | advice   | contextualAdvice   | documentationReference                       | expected
        "summary" | "removalDetails" | null     | null               | null                                         | "summary removalDetails"
        "summary" | "removalDetails" | "advice" | null               | null                                         | "summary removalDetails advice"
        "summary" | "removalDetails" | "advice" | "contextualAdvice" | null                                         | "summary removalDetails contextualAdvice advice"
        "summary" | "removalDetails" | "advice" | "contextualAdvice" | Documentation.userManual("userguide", "bar") | "summary removalDetails contextualAdvice advice ${new DocumentationRegistry().getDocumentationRecommendationFor("information", "userguide", "bar")}"
    }

    def "returns documentation url"() {
        given:
        def featureUsage = new DeprecatedFeatureUsage("summary", "removalDetails", "advice", "contextualAdvice", documentationReference, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, "id display name", "id", getClass())

        expect:
        featureUsage.getDocumentationUrl()?.getUrl() == expected

        where:
        documentationReference                 | expected
        null                                   | null
        Documentation.userManual("foo", "bar") | "https://docs.gradle.org/${GradleVersion.current().version}/userguide/foo.html#bar"
        Documentation.upgradeGuide(42, "bar")  | "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_42.html#bar"
    }
}
