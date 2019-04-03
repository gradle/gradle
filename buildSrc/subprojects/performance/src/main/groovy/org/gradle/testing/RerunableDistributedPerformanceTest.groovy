/*
 * Copyright 2019 the original author or authors.
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
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import org.gradle.initialization.BuildCancellationToken

import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong

@CompileStatic
@CacheableTask
class RerunableDistributedPerformanceTest extends DistributedPerformanceTest {

    @Inject
    RerunableDistributedPerformanceTest(BuildCancellationToken cancellationToken) {
        super(cancellationToken)
        getOutputs().doNotCacheIf("rerun build depends on first run's output", { isRerun() })
    }

    @TaskAction
    @Override
    void executeTests() {
        try {
            super.executeTests()
        } finally {
            writeBinaryResults()
        }
    }

    @Override
    protected void generatePerformanceReport() {
        if (!isFailedFirstRun()) {
            super.generatePerformanceReport()
        } else {
            generateResultsJson()
        }
    }

    /**
     * This is for tagging plugin. See https://github.com/gradle/ci-health/blob/3e30ea146f594ee54a4efe4384f933534b40739c/gradle-build-tag-plugin/src/main/groovy/org/gradle/ci/tagging/plugin/TagSingleBuildPlugin.groovy
     */
    @VisibleForTesting
    void writeBinaryResults() {
        AtomicLong counter = new AtomicLong()
        Map<String, List<ScenarioResult>> classNameToScenarioNames = finishedBuilds.values().findAll { it.testClassFullName != null }.groupBy { it.testClassFullName }
        List<TestClassResult> classResults = classNameToScenarioNames.entrySet().collect { Map.Entry<String, List<ScenarioResult>> entry ->
            TestClassResult classResult = new TestClassResult(counter.incrementAndGet(), entry.key, 0L)
            entry.value.each { ScenarioResult scenarioResult ->
                classResult.add(scenarioResult.toMethodResult(counter))
            }
            classResult
        }

        new TestResultSerializer(getBinResultsDir()).write(classResults)
    }

    private boolean isFailedFirstRun() {
        return !isRerun() && !finishedBuilds.values().every { it.successful }
    }

    private boolean isRerun() {
        return Boolean.parseBoolean(project.findProperty("onlyPreviousFailedTestClasses")?.toString())
    }

    @Override
    protected List<ScenarioBuildResultData> generateResultsForReport() {
        List<ScenarioBuildResultData> resultData = readFirstRunResultData()
        resultData.addAll(super.generateResultsForReport())
        return resultData
    }

    List<ScenarioBuildResultData> readFirstRunResultData() {
        if (isRerun() && resultsJson.isFile()) {
            List<Map> resultList = (List<Map>) new JsonSlurper().parseText(resultsJson.text)
            return resultList.collect { new ScenarioBuildResultData(it) }
        } else {
            return []
        }
    }
}
