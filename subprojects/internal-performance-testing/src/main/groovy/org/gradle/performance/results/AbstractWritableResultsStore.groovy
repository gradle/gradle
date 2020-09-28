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

package org.gradle.performance.results

import com.google.common.collect.ImmutableMap
import groovy.transform.CompileStatic

import java.sql.Timestamp
import java.time.LocalDateTime

@CompileStatic
abstract class AbstractWritableResultsStore<T extends PerformanceTestResult> implements WritableResultsStore<T> {
    private final PerformanceDatabase db

    AbstractWritableResultsStore(PerformanceDatabase db) {
        this.db = db
    }

    private static final int LATEST_EXECUTION_TIMES_DAYS = 14
    private static final String SELECT_LATEST_EXECUTION_TIMES = '''
        with last as
        (
           select
           testClass,
           testId,
           testProject,
           operatingSystem,
           max(id) as lastId
           from testExecution
           where startTime > ?
           and (channel in (?, ?))
           and testProject is not null
           group by testClass,
           testId,
           testProject
        )
        select
        last.testClass,
        last.testId,
        last.testProject,
        testExecution.startTime,
        testExecution.endTime
        from last
        join testExecution on testExecution.id = last.lastId
        order by last.testClass,last.testId,last.testProject
    '''

    @Override
    public Map<PerformanceExperiment, Long> getEstimatedExperimentTimesInMillis(OperatingSystem operatingSystem) {
        return this.<Map<PerformanceExperiment, Long>>withConnection("load estimated runtimes") { connection ->
            Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusDays(LATEST_EXECUTION_TIMES_DAYS))
            ImmutableMap.Builder<PerformanceExperiment, Long> builder = ImmutableMap.builder()
            connection.prepareStatement(SELECT_LATEST_EXECUTION_TIMES).withCloseable { statement ->
                statement.setTimestamp(1, since)
                List<String> channels = ['commits', 'experiments'].stream()
                    .collect { channel -> "${channel}${operatingSystem.getChannelSuffix()}-master".toString() }
                statement.setString(2, channels.get(0))
                statement.setString(3, channels.get(1))
                statement.executeQuery().withCloseable { experimentTimes ->
                    while (experimentTimes.next()) {
                        String testClass = experimentTimes.getString(1)
                        String testName = experimentTimes.getString(2)
                        String testProject = experimentTimes.getString(3)
                        long startTime = experimentTimes.getTimestamp(4).getTime()
                        long endTime = experimentTimes.getTimestamp(5).getTime()
                        if (testProject != null && testClass != null) {
                            PerformanceExperiment performanceExperiment = new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testName))
                            builder.put(performanceExperiment, endTime - startTime)
                        }
                    }
                    return builder.build()
                }
            }
        }
    }

    protected <RESULT> RESULT withConnection(String actionName, ConnectionAction<RESULT> action) {
        return db.withConnection(actionName, action)
    }


    @Override
    void close() {
        db.close()
    }
}
