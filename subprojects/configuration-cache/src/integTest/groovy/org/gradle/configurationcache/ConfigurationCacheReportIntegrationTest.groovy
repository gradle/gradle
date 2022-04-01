/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import com.microsoft.playwright.Playwright
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf

import java.util.function.Consumer

import static org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheProblemsFixture.resolveConfigurationCacheReport


@Requires(TestPrecondition.NOT_LINUX) // Playwright has package dependencies on linux
@IgnoreIf({ GradleContextualExecuter.isNoDaemon() })
class ConfigurationCacheReportIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "report with problem loads successfully"() {
        given:
        buildFile '''
            tasks.register('notOk') {
                doLast { println project.name }
            }
        '''

        when:
        configurationCacheFails 'notOk'

        then:
        def configurationCacheReport = resolveConfigurationCacheReport(testDirectory, failure.error)
        configurationCacheReport != null

        and:
        def pageErrors = []
        def activeGroup = selectInnerTextOf(
            configurationCacheReport,
            "div.group-selector.group-selector--active",
            { pageErrors.add(it) }
        )
        pageErrors == []
        activeGroup == "Problems grouped by message1"
    }

    private String selectInnerTextOf(File configurationCacheReport, String selector, Consumer<String> onPageError) {
        try (Playwright playwright = Playwright.create()) {
            def browser = playwright.webkit().launch()
            def context = browser.newContext()
            def page = context.newPage()
            page.onPageError(onPageError)
            page.navigate(configurationCacheReport.toURI().toString())
            page.innerText(selector)
        }
    }
}
