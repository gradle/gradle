/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.docs.releasenotes

import geb.Browser
import geb.Configuration
import geb.navigator.Navigator
import geb.spock.GebReportingSpec
import groovy.json.JsonSlurper
import org.gradle.util.GradleVersion
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import spock.lang.IgnoreIf
import spock.lang.Shared

/**
 * These tests actually open the release notes in a browser and test the JS.
 */
@IgnoreIf({ !canReachServices() })
class FunctionalReleaseNotesTest extends GebReportingSpec {

    static private final String FIXED_ISSUES_URL = "http://services.gradle.org/fixed-issues/${GradleVersion.current().versionBase}"
    static private final String KNOWN_ISSUES_URL = "http://services.gradle.org/known-issues/${GradleVersion.current().versionBase}"

    static boolean canReachServices() {
        try {
            HttpURLConnection connection = FIXED_ISSUES_URL.toURL().openConnection()
            connection.requestMethod = "HEAD"
            connection.connect()
            connection.responseCode == 200
        } catch (ignored) {
            false
        }
    }

    @Shared url = new ReleaseNotesTestContext().renderedFile.toURL().toString()

    def setup() {
        go url
    }

    @Override
    Browser createBrowser() {
        new Browser(driver: new HtmlUnitDriver(true), new Configuration(reportsDir: new File("build/geb-reports")))
    }

    List<Map> fixedIssues() {
        new JsonSlurper().parseText(new URL(FIXED_ISSUES_URL).text)
    }

    List<Map> knownIssues() {
        new JsonSlurper().parseText(new URL(KNOWN_ISSUES_URL).text)
    }

    def "has fixed issues"() {
        when:
        Navigator paragraphAfterHeading = waitFor { $("#fixed-issues").next("p") }
        def matcher = paragraphAfterHeading.text() =~ /(\d+) issues have been fixed in Gradle [\d\.]+/
        def fixed = fixedIssues()
        def numFixedIssues = fixed.size()

        then:
        matcher.matches()
        matcher.group(1) == numFixedIssues.toString()
        paragraphAfterHeading.next().is("ul") == numFixedIssues > 0
        if (numFixedIssues == 0) {
            return
        }
        def issues = $("ul#fixed-issues-list li")
        issues.size() == numFixedIssues
        fixed.eachWithIndex { json, i ->
            def issue = issues[i]
            assert issue.text() == "[$json.key] - ${json.summary.trim()}"
            assert issue.find("a").attr("href") == json.link
        }
    }

    def "has known issues"() {
        when:
        Navigator paragraphAfterHeading = waitFor { $("#known-issues").next("p").next("p") }
        def knownIssues = knownIssues()

        then:
        if (knownIssues.size() == 0) {
            assert paragraphAfterHeading.text() == "There are no known issues of Gradle ${GradleVersion.current().versionBase} at this time."
            return
        }

        paragraphAfterHeading.text() == "There are ${knownIssues.size()} known issues of Gradle ${GradleVersion.current().versionBase}"
        paragraphAfterHeading.next().is("ul")
        def issues = $("ul#known-issues-list li")
        issues.size() == knownIssues.size()
        knownIssues.eachWithIndex { json, i ->
            def issue = issues[i]
            assert issue.text() == "[$json.key] - ${json.summary.trim()}"
            assert issue.find("a").attr("href") == json.link
        }
    }

    def cleanupSpec() {
        browser.quit()
    }
}
