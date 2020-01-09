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

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Splitter
import com.google.common.collect.HashMultiset
import com.google.common.collect.Multiset
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import org.apache.commons.io.FileUtils
import org.apache.commons.io.input.CloseShieldInputStream
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.initialization.BuildCancellationToken
import org.gradle.process.CommandLineArgumentProvider
import org.openmbee.junit.JUnitMarshalling
import org.openmbee.junit.model.JUnitTestSuite

import javax.inject.Inject
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Runs each performance test scenario in a dedicated TeamCity job.
 *
 * The test runner is instructed to just write out the list of scenarios
 * to run instead of actually running the tests. Then this list is used
 * to schedule TeamCity jobs for each individual scenario. This task
 * blocks until all the jobs have finished and aggregates their status.
 */
@CompileStatic
@CacheableTask
abstract class DistributedPerformanceTest extends PerformanceTest {

    @Input
    String buildTypeId

    @Input
    String workerTestTaskName

    @Input
    String teamCityUrl

    @Input
    String teamCityToken

    @OutputFile
    File scenarioList

    @Internal
    DefaultPerformanceReporter distributedPerformanceReporter

    private RESTClient client
    private final Property<PerformanceScenarioRerunStrategy> rerunStrategy = project.objects
        .property(PerformanceScenarioRerunStrategy)
        .convention(PerformanceScenarioRerunStrategy.NEVER)

    protected Map<String, Scenario> scheduledBuilds = [:]

    /**
     * The rerun strategy to use.
     *
     * Use {@link #repeatScenarios(int)} or {@link #retryFailedScenarios(int)} to set a strategy.
     * By default, no reruns happen.
     */
    @Nested
    @PackageScope
    Property<PerformanceScenarioRerunStrategy> getRerunStrategy() {
        return rerunStrategy
    }

    @Internal
    @VisibleForTesting
    Map<String, ScenarioResult> finishedBuilds = [:]

    private final JUnitXmlTestEventsGenerator testEventsGenerator

    private final BuildCancellationToken cancellationToken

    @Inject
    DistributedPerformanceTest(BuildCancellationToken cancellationToken) {
        this.testEventsGenerator = new JUnitXmlTestEventsGenerator(listenerManager.createAnonymousBroadcaster(TestListener.class), listenerManager.createAnonymousBroadcaster(TestOutputListener.class))
        this.cancellationToken = cancellationToken
        jvmArgumentProviders.add(new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ["-Dorg.gradle.performance.scenario.list=$scenarioList".toString()]
            }
        })
    }

    @Override
    void addTestListener(TestListener listener) {
        testEventsGenerator.addTestListener(listener)
    }

    @Override
    void addTestOutputListener(TestOutputListener listener) {
        testEventsGenerator.addTestOutputListener(listener)
    }

    void setScenarioList(File scenarioList) {
        this.scenarioList = scenarioList
    }

    /**
     * Repeat scenarios.
     *
     * The scenario is repeated regardless of its outcome.
     *
     * @param times number of times a scenario should be retried.
     */
    void repeatScenarios(int times) {
        rerunStrategy.set(new RepeatRerunStrategy(times))
    }

    /**
     * Retry failed scenarios.
     *
     * @param maxRetryCount maximum number of retries for a scenario
     */
    void retryFailedScenarios(int maxRetryCount = 1) {
        rerunStrategy.set(new RetryFailedRerunStrategy(maxRetryCount))
    }

    @TaskAction
    @Override
    void executeTests() {
        println("Running against baseline ${determinedBaselines.getOrElse('defaults')}")
        try {
            doExecuteTests()
        } catch (Throwable e) {
            e.printStackTrace()
            throw e
        } finally {
            distributedPerformanceReporter.report(this)
            testEventsGenerator.release()
        }
    }

    @Override
    protected void generateResultsJson() {
        List<ScenarioBuildResultData> resultData = getResultsFromCurrentRun()
        FileUtils.write(resultsJson, JsonOutput.toJson(resultData), Charset.defaultCharset())
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private List<ScenarioBuildResultData> getResultsFromCurrentRun() {
        return finishedBuilds.collect { workerBuildId, scenarioResult ->
            new ScenarioBuildResultData(
                teamCityBuildId: workerBuildId,
                scenarioName: scheduledBuilds.get(workerBuildId).id,
                scenarioClass: scenarioResult.testClassFullName,
                webUrl: scenarioResult.buildResponse.webUrl,
                status: scenarioResult.buildResponse.status,
                agentName: scenarioResult.buildResponse.agent.name,
                agentUrl: scenarioResult.buildResponse.agent.webUrl,
                testFailure: scenarioResult.failureText)
        }
    }

    private void doExecuteTests() {
        scenarioList.delete()

        fillScenarioList()

        def scenarios = scenarioList.readLines().collect { String line -> new Scenario(line) }.sort { -it.estimatedRuntime }

        createClient()

        def coordinatorBuild = resolveCoordinatorBuild()
        testEventsGenerator.coordinatorBuild = coordinatorBuild

        def lastChangeId = coordinatorBuild?.lastChangeId
        scenarios.each {
            schedule(it, lastChangeId)
        }
        waitForTestsCompletion(lastChangeId)
    }

    private void fillScenarioList() {
        super.executeTests()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void schedule(Scenario scenario, String lastChangeId) {
        def requestBody = [
            buildTypeId: buildTypeId,
            properties: [
                property: [
                    [name: 'scenario', value: scenario.id],
                    [name: 'templates', value: scenario.templates.join(' ')],
                    [name: 'baselines', value: determinedBaselines.getOrElse('defaults')],
                    [name: 'warmups', value: warmups ?: 'defaults'],
                    [name: 'runs', value: runs ?: 'defaults'],
                    [name: 'checks', value: checks ?: 'all'],
                    [name: 'channel', value: channel ?: 'commits']
                ]
            ]
        ]

        if (branchName) {
            requestBody['branchName'] = branchName
        }
        if (lastChangeId) {
            requestBody['lastChanges'] = [change: [[id: lastChangeId]]]
        }
        println("Scheduling $scenario.id, estimated runtime: $scenario.estimatedRuntime, coordinatorBuildId: $buildId, lastChangeId: $lastChangeId, build request: $requestBody")

        Map response = httpPost(path: 'buildQueue', requestContentType: ContentType.JSON, body: JsonOutput.toJson(requestBody))

        /*
        {
            "id": 14585813,
            "buildTypeId": "Gradle_Check_NoDaemon_Java8_Oracle_Windows_workers",
            "number": "921",
            "status": "FAILURE",
            "state": "finished",
            "branchName": "master",
            "href": "/app/rest/builds/id:14585813",
            "webUrl": "https://builds.gradle.org/viewLog.html?buildId=14585813&buildTypeId=Gradle_Check_NoDaemon_Java8_Oracle_Windows_workers",
            "statusText": "Gradle exception (new); exit code 1 (new)",
            "buildType": {
                "id": "Gradle_Check_NoDaemon_Java8_Oracle_Windows_workers",
                "name": "Test Coverage - NoDaemon Java8 Oracle Windows (workers)",
                "projectName": "Gradle / Check / Release Accept / Test Coverage - NoDaemon Java8 Oracle Windows",
                "projectId": "Gradle_Check_NoDaemon_Java8_Oracle_Windows",
                "href": "/app/rest/buildTypes/id:Gradle_Check_NoDaemon_Java8_Oracle_Windows_workers",
                "webUrl": "https://builds.gradle.org/viewType.html?buildTypeId=Gradle_Check_NoDaemon_Java8_Oracle_Windows_workers"
            },
            "lastChanges": {
                "change": [{
                    "id": 476592,
                    "version": "46ea4a59b549acea726dde8caa87307237a9679e",
                    "username": "gary",
                    "date": "20180730T194944+0000",
                    "href": "/app/rest/changes/id:476592",
                    "webUrl": "https://builds.gradle.org/viewModification.html?modId=476592&personal=false"
                }],
                "count": 1
            },
            ...
        }
        */

        String workerBuildId = response.id

        println("Scheduled ${scenario.id} and worker id: ${workerBuildId}")
        cancellationToken.addCallback {
            cancel(workerBuildId)
        }
        def scheduledChangeId = findLastChangeIdInJson(response)
        if (lastChangeId && lastChangeId != scheduledChangeId) {
            throw new RuntimeException("The requested change id is different than the actual one. requested change id: $lastChangeId in coordinatorBuildId: $buildId, actual change id: $scheduledChangeId in workerBuildId: $workerBuildId\nresponse: $response")
        }
        scheduledBuilds.put(workerBuildId, scenario)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private CoordinatorBuild resolveCoordinatorBuild() {
        if (buildId) {
            Map response = httpGet(path: "builds/id:$buildId")
            return new CoordinatorBuild(id: buildId, lastChangeId: findLastChangeIdInJson(response), buildTypeId: response.buildTypeId)
        }
        return null
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private static String findLastChangeIdInJson(Map responseJson) {
        responseJson?.lastChanges?.change?.get(0)?.id
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private Map httpGet(Map params) {
        try {
            HttpResponseDecorator resp = client.get(params)
            if (ContentType.JSON.toString() == resp.getContentType()) {
                return resp.data
            } else {
                // Sometimes, TC returns text/html page
                // https://github.com/gradle/gradle-private/issues/1359
                System.err.println("""
                |Got TeamCity HTML response when accepting application/json:

                |${resp.getStatusLine()}
                |${resp.data}
                """.stripMargin())
                return [state: 'unknown']
            }
        } catch (HttpResponseException ex) {
            println("Get response ${ex.response.status}\n${ex.response.data}")
            throw ex
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private Map httpPost(Map params) {
        try {
            return client.post(params).data
        } catch (HttpResponseException ex) {
            println("Get response ${ex.response.status}\n${ex.response.data}")
            throw ex
        }
    }

    void waitForTestsCompletion(String lastChangeId) {
        Set<String> completed = []
        Multiset<String> completedScenarios = HashMultiset.create()
        while (completed.size() < scheduledBuilds.size()) {
            List<String> waiting = []
            List<Scenario> scenariosToReSchedule = []
            scheduledBuilds.each { buildId, scenario ->
                if (!completed.contains(buildId)) {
                    if (checkResult(buildId)) {
                        completed << buildId
                        def scenarioName = scenario.getId()
                        completedScenarios.add(scenarioName)
                        def finishedBuild = finishedBuilds.get(buildId)
                        if (rerunStrategy.get().shouldRerun(completedScenarios.count(scenarioName), finishedBuild.isSuccessful())) {
                            scenariosToReSchedule.add(scenario)
                        }
                    } else {
                        waiting << buildId
                    }
                }
            }
            scenariosToReSchedule.each { schedule(it, lastChangeId) }
            if (completed.size() < scheduledBuilds.size()) {
                int total = scheduledBuilds.size()
                int pc = (100 * (((double) completed.size()) / (double) total)) as int
                println "Waiting for scenarios $waiting to complete"
                println "Completed ${completed.size()} tests of $total ($pc%)"
                sleep(TimeUnit.MINUTES.toMillis(1))
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private boolean checkResult(String jobId) {
        Map response = httpGet(path: "builds/id:$jobId", requestContentType: ContentType.JSON)
        boolean finished = response.state == "finished"
        if (finished) {
            collectPerformanceTestResults(response, jobId)
        }
        finished
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void collectPerformanceTestResults(Map response, String jobId) {
        try {
            JUnitTestSuite testSuite = fetchTestResult(response)
            finishedBuilds.put(jobId, new ScenarioResult(name: scheduledBuilds.get(jobId).id, testClassFullName: scheduledBuilds.get(jobId).className, testSuite: testSuite, buildResponse: response))
            fireTestListener(testSuite, response)
        } catch (e) {
            e.printStackTrace(System.err)
            finishedBuilds.put(jobId, new ScenarioResult(name: scheduledBuilds.get(jobId).id, testClassFullName: scheduledBuilds.get(jobId).className, buildResponse: response))
        }
    }

    void cancel(String buildId) {
        try {
            println("Cancelling: " + buildId)
            cancel(buildId, "buildQueue")
        } catch (HttpResponseException eq) {
            rethrowIfNonRecoverable(eq)
            try {
                cancel(buildId, "builds")
            } catch (HttpResponseException eb) {
                rethrowIfNonRecoverable(eb)
            }
        }
    }

    private static void rethrowIfNonRecoverable(HttpResponseException e) {
        if (e.statusCode != 404) {
            throw e
        }
    }

    void cancel(String workerBuildId, String endpoint) {
        String link = "$teamCityUrl/viewLog.html?buildId=$buildId&buildTypeId=$buildTypeId"
        Map cancelRequest = [buildCancelRequest: [commend: "Coordinator build was canceled: $link", readdIntoQueue: "false"]]
        httpPost(path: "$endpoint/id:$workerBuildId", requestContentType: ContentType.JSON, body: JsonOutput.toJson(cancelRequest))
    }

    private void fireTestListener(JUnitTestSuite result, Map buildResult) {
        testEventsGenerator.processTestSuite(result, buildResult)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private JUnitTestSuite fetchTestResult(Map buildData) {
        JUnitTestSuite testSuite = null
        def artifactsUri = buildData?.artifacts?.href
        if (artifactsUri) {
            def resultArtifacts = httpGet(path: "${artifactsUri}/results/${project.name}/build/")
            /*
                {
                    "count": 1,
                    "file": [
                    {
                        "name": "test-results-fullPerformanceTest.zip",
                        "size": 972,
                        "modificationTime": "20180921T004828+0000",
                        "href": "/app/rest/9.1/builds/id:15973459/artifacts/metadata/results/performance/build/test-results-fullPerformanceTest.zip",
                        "content": {
                        "href": "/app/rest/9.1/builds/id:15973459/artifacts/content/results/performance/build/test-results-fullPerformanceTest.zip"
                    }
                    }
                ]
                }
            */
            if (resultArtifacts.count > 0) {
                def zipName = "test-results-${workerTestTaskName}.zip".toString()
                def fileNode = resultArtifacts.file.find { it.name == zipName }
                if (fileNode) {
                    def contentUri = fileNode.content.href
                    client.get(path: contentUri, contentType: ContentType.BINARY) {
                        resp, inputStream ->
                            testSuite = parseXmlsInZip(buildData, inputStream)
                    }
                }
            }
        }
        return testSuite
    }

    private static JUnitTestSuite parseXmlsInZip(Map response, InputStream inputStream) {
        List<JUnitTestSuite> parsedXmls = []
        new ZipInputStream(inputStream).withStream { zipInput ->
            def entry
            while (entry = zipInput.nextEntry) {
                if (!entry.isDirectory() && entry.name.endsWith('.xml')) {
                    parsedXmls.add(JUnitMarshalling.unmarshalTestSuite(new CloseShieldInputStream(zipInput)))
                }
            }
        }
        assert parsedXmls.size() == 1: "Error when parsing xml: ${response}"
        parsedXmls[0]
    }

    private RESTClient createClient() {
        client = new RESTClient("$teamCityUrl/app/rest/9.1")
        client.headers.putAt('Authorization', "Bearer $teamCityToken")
        client.headers.putAt('Origin', teamCityUrl)
        client.headers.putAt('Accept', ContentType.JSON.toString())
        client
    }

    private static class Scenario {
        String className
        String id
        long estimatedRuntime
        List<String> templates

        Scenario(String scenarioLine) {
            def parts = Splitter.on(';').split(scenarioLine).toList()
            this.className = parts[0]
            this.id = parts[1]
            this.estimatedRuntime = parts[2].toLong()
            this.templates = parts[3..-1]
        }
    }

    static class ScenarioResult {
        String name
        String testClassFullName
        JUnitTestSuite testSuite
        Map buildResponse

        boolean isSuccessful() {
            return buildResponse.status == 'SUCCESS'
        }

        @TypeChecked(TypeCheckingMode.SKIP)
        String getFailureText() {
            if (testSuite) {
                collectFailures(testSuite)
            } else {
                return buildResponse.statusText
            }
        }
    }
}
