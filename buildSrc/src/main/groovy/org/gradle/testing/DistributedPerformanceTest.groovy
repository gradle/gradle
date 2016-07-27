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

package org.gradle.testing

import com.google.common.base.Splitter
import com.google.common.collect.Lists
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.concurrent.TimeUnit

@CompileStatic
class DistributedPerformanceTest extends PerformanceTest {

    @Input
    String buildyTypeId

    @Input
    String teamCityUrl

    @Input
    String teamCityUsername

    @Input
    String teamCityPassword

    @OutputFile
    File scenarioList

    RESTClient client

    List<String> scheduledJobs = Lists.newArrayList()

    @TaskAction
    void executeTests() {
        scenarioList.delete()

        super.executeTests()

        def scenarios = scenarioList.readLines().collect { line ->
            def parts = Splitter.on(',').split(line)
            new Scenario(id : parts.head(), templates: parts.tail().toList())
        }

        createClient()

        scenarios.each { Scenario scenario ->
            schedule(scenario)
        }

        scheduledJobs.each {
            join(it)
        }
    }

    void setScenarioList(File scenarioList) {
        systemProperty "org.gradle.performance.scenario.list", scenarioList
        this.scenarioList = scenarioList
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void schedule(Scenario scenario) {
        def response = client.post(
            path: "buildQueue",
            requestContentType: ContentType.XML,
            body: """
                <build>
                    <buildType id="${buildyTypeId}"/>
                    <properties>
                        <property name="scenario" value="${scenario.id}"/>
                        <property name="templates" value="${scenario.templates.join(' ')}"/>
                    </properties>
                </build>
            """
        )

        scheduledJobs += response.data.@id
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void join(String jobId) {
        def finished = false
        while (!finished) {
            def response = client.get(path: "builds/id:$jobId/state")
            finished = response.data.text == "finished"
            if (!finished) {
                sleep(TimeUnit.MINUTES.toMillis(1))
            }
        }
    }

    private RESTClient createClient() {
        client = new RESTClient("$teamCityUrl/httpAuth/app/rest/")
        client.auth.basic(teamCityUsername, teamCityPassword)
        client
    }

    private static class Scenario {
        String id
        List<String> templates
    }
}
