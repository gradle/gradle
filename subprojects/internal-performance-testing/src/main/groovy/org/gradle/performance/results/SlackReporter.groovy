/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.results

import com.google.common.base.Charsets
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.mortbay.util.ajax.JSON

class SlackReporter implements DataReporter<CrossVersionPerformanceResults> {
    private static final String SLACK_WEBHOOK_URL_ENV_VAR = "SLACK_WEBHOOK_URL"
    private static final String[] EXCLAMATIONS = ["Hurrah!", "Whoopee!", "Excellent!", "Yippee!", "Wow!", "OMG!", "Amazing!", "Fantastic!", "Awesome!", "Brilliant!"]
    private final URI webhook
    private final CloseableHttpClient httpClient
    private final DataReporter<CrossVersionPerformanceResults> delegate

    SlackReporter(URI webhook, DataReporter<CrossVersionPerformanceResults> delegate) {
        this.delegate = delegate
        this.webhook = webhook
        this.httpClient = HttpClients.createDefault()
    }

    @Override
    void report(CrossVersionPerformanceResults results) {
        delegate.report(results)

        def significantlyFasterThanBaselines = results.baselineVersions.every { it.significantlySlowerThan(results.current) }
        if (true || significantlyFasterThanBaselines) {
            def exclamation = EXCLAMATIONS[new Random().nextInt(EXCLAMATIONS.length)]

            def title = "Results for `${results.testProject}` with tasks `${results.tasks.join('`, `')}`"
            if (results.cleanTasks) {
                title += ", cleaned with `${results.cleanTasks.join('`, `')}`"
            }

            def compareLink = "<https://github.com/gradle/gradle/compare/${results.vcsCommits.first()}^...${results.vcsCommits.last()}|recent changes>"
            def commits = "`" + results.vcsCommits.collect { it.substring(0, 7) }.join("`, `") + "`"
            def message = "$exclamation ${title} look significantly faster on `${results.vcsBranch}` after ($compareLink})\nCommits: $commits"

            def json = JSON.toString(
                text: message,
                username: "Performance tests",
                icon_emoji: ":dash:",
                channel: "#test-lptr"
            )

            def post = new HttpPost(webhook)
            post.setHeader("Accept", "application/json")
            post.setHeader("Content-type", "application/json")
            post.setEntity(new StringEntity(json, Charsets.UTF_8))
            httpClient.execute(post)
        }
    }

    static DataReporter<CrossVersionPerformanceResults> wrap(DataReporter<CrossVersionPerformanceResults> reporter) {
        def slackWebhookUrl = System.getenv(SLACK_WEBHOOK_URL_ENV_VAR)
        if (slackWebhookUrl) {
            new SlackReporter(URI.create(slackWebhookUrl), reporter)
        } else {
            return reporter
        }
    }

    @Override
    void close() {
        try {
            httpClient.close()
        } finally {
            delegate.close()
        }
    }
}
