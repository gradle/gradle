/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.docs.samples

import spock.lang.Specification

class SampleFailureMessageFormatterTest extends Specification {

    private static final String BANNER = "**********************************************************************************"

    def "non-config-cache sample under default executer produces plain reproduce command"() {
        when:
        def message = SampleFailureMessageFormatter.format("snippet-some-other-area-thing_groovy_doSomething", false)

        then:
        message == [
            "Sample test run failed.",
            "To understand how docsTest works, See:",
            "  https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/README.md#testing-docs",
            BANNER,
            "To reproduce this failure, run:",
            "  ./gradlew docs:docsTest --tests '*snippet-some-other-area-thing_groovy_doSomething*'",
            BANNER
        ].join("\n")
    }

    def "non-config-cache sample under configCache executer appends -P flag"() {
        when:
        def message = SampleFailureMessageFormatter.format("snippet-some-other-area-thing_groovy_doSomething", true)

        then:
        message.contains("--tests '*snippet-some-other-area-thing_groovy_doSomething*' -PenableConfigurationCacheForDocsTests=true")
        !message.contains("Remember to set enableConfigurationCacheForDocsTests=false")
    }

    def "config-cache sample under default executer omits -P flag and shows warning"() {
        given:
        def sampleId = "snippet-optimizing-builds-configuration-cache-problems-fixed-reuse_groovy_configurationCacheProblemsFixedReuse"

        when:
        def message = SampleFailureMessageFormatter.format(sampleId, false)

        then:
        message.contains("  ./gradlew docs:docsTest --tests '*${sampleId}*'\n")
        !message.contains("-PenableConfigurationCacheForDocsTests=true")
        message.contains("  Remember to set enableConfigurationCacheForDocsTests=false or the test will not be found.")
    }

    def "config-cache sample under configCache executer still omits -P flag (otherwise test would be filtered out) and shows warning"() {
        given:
        def sampleId = "snippet-optimizing-builds-configuration-cache-problems-fixed-reuse_groovy_configurationCacheProblemsFixedReuse"

        when:
        def message = SampleFailureMessageFormatter.format(sampleId, true)

        then:
        !message.contains("-PenableConfigurationCacheForDocsTests=true")
        message.contains("  Remember to set enableConfigurationCacheForDocsTests=false or the test will not be found.")
    }

    def "WithoutCC sample under default executer omits -P flag and shows warning"() {
        given:
        def sampleId = "snippet-some-feature_groovy_doSomethingWithoutCC"

        when:
        def message = SampleFailureMessageFormatter.format(sampleId, false)

        then:
        message.contains("  ./gradlew docs:docsTest --tests '*${sampleId}*'\n")
        !message.contains("-PenableConfigurationCacheForDocsTests=true")
        message.contains("  Remember to set enableConfigurationCacheForDocsTests=false or the test will not be found.")
    }

    def "message is wrapped by visible asterisk banners on both sides of the reproduce block"() {
        when:
        def message = SampleFailureMessageFormatter.format("snippet-anything_groovy_x", false)
        def lines = message.split("\n")
        def bannerIndices = (0..<lines.length).findAll { lines[it] == BANNER }

        then:
        bannerIndices.size() == 2
        // The "To reproduce this failure, run:" line must sit strictly between the two banners.
        def reproIdx = (0..<lines.length).find { lines[it] == "To reproduce this failure, run:" }
        bannerIndices[0] < reproIdx
        reproIdx < bannerIndices[1]
    }
}
