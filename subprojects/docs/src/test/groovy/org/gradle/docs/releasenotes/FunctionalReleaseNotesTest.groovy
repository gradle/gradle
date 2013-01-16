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

    private String version = GradleVersion.current().versionBase

    static boolean canReachServices() {
        try {
            HttpURLConnection connection = FIXED_ISSUES_URL.toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connect()
            connection.responseCode == 200
        } catch (ignored) {
            false
        }
    }

    @Shared url = new ReleaseNotesTestContext().renderedFile.toURL().toString()

    def setup() {
        to ReleaseNotesPage
    }

    @Override
    ReleaseNotesPage getPage() {
        browser.page as ReleaseNotesPage
    }

    @Override
    Browser createBrowser() {
        new Browser(driver: new HtmlUnitDriver(true), new Configuration(reportsDir: new File("build/geb-reports")))
    }

    List<Map> fixedIssues() {
        new JsonSlurper().parseText(new URL(FIXED_ISSUES_URL).text) as List<Map>
    }

    List<Map> knownIssues() {
        new JsonSlurper().parseText(new URL(KNOWN_ISSUES_URL).text) as List<Map>
    }

    def "has fixed issues"() {
        when:
        def fixed = fixedIssues()
        def numFixedIssues = fixed.size()

        then:
        waitFor { page.fixedIssuesParagraph.text() == "$numFixedIssues issues have been fixed in Gradle $version." }
        if (numFixedIssues == 0) {
            return
        }

        page.fixedIssuesListItems.size() == numFixedIssues
        fixed.eachWithIndex { json, i ->
            def issue = page.fixedIssuesListItems[i]
            assert issue.text() == "[$json.key] - ${json.summary.trim()}"
            assert issue.find("a").attr("href") == json.link
        }
    }

    def "has known issues"() {
        when:
        def knownIssues = knownIssues()

        then:
        if (knownIssues.size() == 0) {
            waitFor { page.knownIssuesParagraph.text() == "There are no known issues of Gradle ${version} at this time." }
            return
        } else {
            waitFor { page.knownIssuesParagraph.text() == "There are ${knownIssues.size()} known issues of Gradle $version." }
        }

        page.knownIssuesListItems.size() == knownIssues.size()
        knownIssues.eachWithIndex { json, i ->
            def issue = page.knownIssuesListItems[i]
            assert issue.text() == "[$json.key] - ${json.summary.trim()}"
            assert issue.find("a").attr("href") == json.link
        }
    }

    def cleanupSpec() {
        browser.quit()
    }
}
