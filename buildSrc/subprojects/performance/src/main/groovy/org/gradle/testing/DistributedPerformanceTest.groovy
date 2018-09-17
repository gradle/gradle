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
import groovy.xml.XmlUtil
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import org.gradle.api.GradleException
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.IoActions
import org.gradle.process.CommandLineArgumentProvider

import javax.inject.Inject
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import org.gradle.api.Action
import org.gradle.process.JavaExecSpec
import groovy.transform.CompileStatic
import org.openmbee.junit.JUnitMarshalling
import org.openmbee.junit.model.JUnitTestSuite
import org.openmbee.junit.model.JUnitTestCase
import org.openmbee.junit.model.JUnitFailure
import org.apache.commons.io.input.CloseShieldInputStream
import groovy.json.JsonOutput

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
class DistributedPerformanceTest extends PerformanceTest {
    @Internal
    String coordinatorBuildId

    @Internal
    String branchName

    @Input
    String buildTypeId

    @Input
    String workerTestTaskName

    @Input
    String teamCityUrl

    @Input
    String teamCityUsername

    @Internal
    String teamCityPassword

    @OutputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    File scenarioList

    @OutputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File reportDir

    private RESTClient client

    private Map<String, Scenario> scheduledBuilds = [:]

    private Map<String, ScenarioResult> finishedBuilds = [:]

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

    @TaskAction
    void executeTests() {
        try {
            doExecuteTests()
        } finally {
            testEventsGenerator.release()
            generatePerformanceReport()
        }
    }

    private File generateResultJson() {
        File resultJson = File.createTempFile('distributedPerformanceTest', 'results.json')
        List<Map> resultData = finishedBuilds.collect { workerBuildId, scenarioResult ->
            // org.gradle.performance.results.ScenarioBuildResultData
            [
                scenarioName: scheduledBuilds.get(workerBuildId).id,
                webUrl: findWebUrlInXml(scenarioResult.buildResultXml),
                successful: findStatusInXml(scenarioResult.buildResultXml),
                testFailure: collectFailures(scenarioResult.testSuite)
            ] as Map
        }
        resultJson.text = JsonOutput.toJson(resultData)
        return resultJson
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String collectFailures(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        List<JUnitFailure> failures = testCases.collect { it.failures ?: [] }.flatten()
        return failures.collect { it.value }.join("\n")
    }

    private void generatePerformanceReport() {
        File resultJson = generateResultJson()
        project.delete(reportDir)
        try {
            project.javaexec(new Action<JavaExecSpec>() {
                void execute(JavaExecSpec spec) {
                    spec.setMain("org.gradle.performance.results.ReportGenerator")
                    spec.args(resultStoreClass, reportDir.path, resultJson.path)
                    spec.systemProperties(databaseParameters)
                    spec.systemProperty("org.gradle.performance.execution.channel", channel)
                    spec.setClasspath(DistributedPerformanceTest.this.getClasspath())
                }
            })
        } finally {
            project.delete(resultJson)
        }
    }

    private void doExecuteTests() {
        scenarioList.delete()

        fillScenarioList()

        def scenarios = scenarioList.readLines().collect { String line -> new Scenario(line) }.sort { -it.estimatedRuntime }

        createClient()

        def coordinatorBuild = resolveCoordinatorBuild()
        testEventsGenerator.coordinatorBuild = coordinatorBuild

        scenarios.each {
            schedule(it, coordinatorBuild?.lastChangeId)
        }

        waitForTestsCompletion()

        checkForErrors()
    }

    private void fillScenarioList() {
        super.executeTests()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void schedule(Scenario scenario, String lastChangeId) {
        def buildRequest = """
                <build${branchName ? " branchName=\"${branchName}\"" : ""}>
                    <buildType id="${buildTypeId}"/>
                    <properties>
                        <property name="scenario" value="${scenario.id}"/>
                        <property name="templates" value="${scenario.templates.join(' ')}"/>
                        <property name="baselines" value="${baselines ?: 'defaults'}"/>
                        <property name="warmups" value="${warmups ?: 'defaults'}"/>
                        <property name="runs" value="${runs ?: 'defaults'}"/>
                        <property name="checks" value="${checks ?: 'all'}"/>
                        <property name="channel" value="${channel ?: 'commits'}"/>
                    </properties>
                    ${renderLastChange(lastChangeId)}
                </build>
            """
        logger.info("Scheduling $scenario.id, estimated runtime: $scenario.estimatedRuntime, coordinatorBuildId: $coordinatorBuildId, lastChangeId: $lastChangeId, build request: $buildRequest")
        def response = client.post(
            path: "buildQueue",
            requestContentType: ContentType.XML,
            body: buildRequest
        )
        String workerBuildId = response.data.@id
        cancellationToken.addCallback {
            cancel(workerBuildId)
        }
        def scheduledChangeId = findLastChangeIdInXml(response.data)
        if (lastChangeId && scheduledChangeId != lastChangeId) {
            throw new RuntimeException("The requested change id is different than the actual one. requested change id: $lastChangeId in coordinatorBuildId: $coordinatorBuildId, actual change id: $scheduledChangeId in workerBuildId: $workerBuildId\nresponse: ${xmlToString(response.data)}")
        }
        scheduledBuilds.put(workerBuildId, scenario)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private static String xmlToString(xmlObject) {
        if (xmlObject != null) {
            try {
                return XmlUtil.serialize(xmlObject)
            } catch (e) {
                // ignore errors
            }
        }
        return null
    }

    private String renderLastChange(lastChangeId) {
        if (lastChangeId) {
            return """
                <lastChanges>
                    <change id="$lastChangeId"/>
                </lastChanges>
            """
        } else {
            return ""
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private CoordinatorBuild resolveCoordinatorBuild() {
        if (coordinatorBuildId) {
            def response = client.get(path: "builds/id:$coordinatorBuildId")
            if (response.success) {
                return new CoordinatorBuild(id: coordinatorBuildId, lastChangeId: findLastChangeIdInXml(response.data), buildTypeId: response.data.@buildTypeId.text())
            }
        }
        return null
    }

    void waitForTestsCompletion() {
        int total = scheduledBuilds.size()
        Set<String> completed = []
        while (completed.size() < total) {
            List<String> waiting = []
            scheduledBuilds.keySet().each { buildId ->
                if (!completed.contains(buildId)) {
                    if (checkResult(buildId)) {
                        completed << buildId
                    } else {
                        waiting << buildId
                    }
                }
            }
            if (completed.size() < total) {
                int pc = (100 * (((double) completed.size()) / (double) total)) as int
                println "Waiting for scenarios $waiting to complete"
                println "Completed ${completed.size()} tests of $total ($pc%)"
                sleep(TimeUnit.MINUTES.toMillis(1))
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String findLastChangeIdInXml(xmlroot) {
        xmlroot.lastChanges.change[0].@id.text()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private boolean checkResult(String jobId) {
        def response = client.get(path: "builds/id:$jobId")
        boolean finished = response.data.@state == "finished"
        if (finished) {
            collectPerformanceTestResults(response, jobId)
        }
        finished
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void collectPerformanceTestResults(def response, String jobId) {
        try {
            JUnitTestSuite testSuite = fetchTestResult(response.data)
            finishedBuilds.put(jobId, new ScenarioResult(name: scheduledBuilds.get(jobId).id, buildResultXml: response.data, testSuite: testSuite))
            fireTestListener(testSuite, response.data)
        } catch (e) {
            e.printStackTrace(System.err)
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private boolean findStatusInXml(xmlroot) {
        xmlroot.@status.toString() == 'SUCCESS'
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String findWebUrlInXml(xmlroot) {
        xmlroot.@webUrl.toString()
    }

    void cancel(String buildId) {
        try {
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

    private void rethrowIfNonRecoverable(HttpResponseException e) {
        if (e.statusCode != 404) {
            throw e
        }
    }

    void cancel(String buildId, String endpoint) {
        String link = XmlUtil.escapeXml("$teamCityUrl/viewLog.html?buildId=$coordinatorBuildId&buildTypeId=$buildTypeId")
        String cancelRequest = """<buildCancelRequest comment="Coordinator build was canceled: $link" readdIntoQueue="false" />"""
        client.post(
            path: "$endpoint/id:$buildId",
            requestContentType: ContentType.XML,
            body: cancelRequest
        )
    }

    private void fireTestListener(JUnitTestSuite result, Object build) {
        testEventsGenerator.processTestSuite(result, build)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private JUnitTestSuite fetchTestResult(buildData) {
        JUnitTestSuite testSuite = null
        def artifactsUri = buildData?.artifacts?.@href?.text()
        if (artifactsUri) {
            def resultArtifacts = client.get(path: "${artifactsUri}/results/${project.name}/build/")
            if (resultArtifacts.success) {
                def zipName = "test-results-${workerTestTaskName}.zip".toString()
                def fileNode = resultArtifacts.data.file.find {
                    it.@name.text() == zipName
                }
                if (fileNode) {
                    def contentUri = fileNode.content.@href.text()
                    client.get(path: contentUri, contentType: ContentType.BINARY) {
                        resp, inputStream ->
                            testSuite = parseXmlsInZip(inputStream)
                    }
                }
            }
        }
        return testSuite
    }

    JUnitTestSuite parseXmlsInZip(InputStream inputStream) {
        List<JUnitTestSuite> parsedXmls = []
        new ZipInputStream(inputStream).withStream { zipInput ->
            def entry
            while (entry = zipInput.nextEntry) {
                if (!entry.isDirectory() && entry.name.endsWith('.xml')) {
                    parsedXmls.add(JUnitMarshalling.unmarshalTestSuite(new CloseShieldInputStream(zipInput)))
                }
            }
        }
        assert parsedXmls.size() == 1
        parsedXmls[0]
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void checkForErrors() {
        def failedBuilds = finishedBuilds.values().findAll { it.buildResultXml.@status != "SUCCESS" }
        if (failedBuilds) {
            throw new GradleException("${failedBuilds.size()} performance tests failed. See $reportDir for details.")
        }
    }

    private RESTClient createClient() {
        client = new RESTClient("$teamCityUrl/httpAuth/app/rest/9.1")
        client.auth.basic(teamCityUsername, teamCityPassword)
        client.headers['Origin'] = teamCityUrl
        client
    }

    private static class Scenario {
        String id
        long estimatedRuntime
        List<String> templates

        Scenario(String scenarioLine) {
            def parts = Splitter.on(';').split(scenarioLine).toList()
            this.id = parts[0]
            this.estimatedRuntime = parts[1].toLong()
            this.templates = parts[2..-1]
        }
    }

    private static class ScenarioResult {
        String name
        Object buildResultXml
        JUnitTestSuite testSuite
    }
}
