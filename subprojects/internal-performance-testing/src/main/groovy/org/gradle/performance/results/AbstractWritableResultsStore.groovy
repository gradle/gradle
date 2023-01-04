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

import javax.annotation.Nonnull
import java.sql.Timestamp
import java.time.LocalDateTime

@CompileStatic
abstract class AbstractWritableResultsStore<T extends PerformanceTestResult> implements WritableResultsStore<T> {
    private final PerformanceDatabase db

    AbstractWritableResultsStore(PerformanceDatabase db) {
        this.db = db
    }

    private static final int LATEST_EXECUTION_TIMES_DAYS = 14
    private static final String SELECT_LATEST_EXECUTION_TIMES = """
        with last as
        (
           ${ OperatingSystem.values().collect { os -> """
            select
               testClass,
               testId,
               testProject,
               '${os.name()}' as os,
               max(id) as lastId
            from testExecution
           where startTime > ?
             and (channel in (?, ?))
             and testProject is not null
           group by testClass, testId, testProject
           """ }.join("UNION") }
        )
        select
        last.testClass,
        last.testId,
        last.testProject,
        last.os,
        testExecution.startTime,
        testExecution.endTime
        from last
        join testExecution on testExecution.id = last.lastId
        order by last.testClass, last.testId, last.testProject, last.os
    """

    @Nonnull
    static String teamcityBuildIdQueryFor(List<String> teamcityBuildIds) {
        return teamcityBuildIds.isEmpty() ? '' : " or teamcitybuildid in (${String.join(',', Collections.nCopies(teamcityBuildIds.size(), '?'))})"
    }

    @Nonnull
    static String channelPatternQueryFor(List<String> channelPatterns) {
        return String.join(' or ', Collections.nCopies(channelPatterns.size(), "channel like ?"))
    }

    @Override
    public Map<PerformanceExperimentOnOs, Long> getEstimatedExperimentDurationsInMillis() {
        return this.<Map<PerformanceExperimentOnOs, Long>>withConnection("load estimated runtimes") { connection ->
            Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusDays(LATEST_EXECUTION_TIMES_DAYS))
            ImmutableMap.Builder<PerformanceExperimentOnOs, Long> builder = ImmutableMap.builder()
            connection.prepareStatement(SELECT_LATEST_EXECUTION_TIMES).withCloseable { statement ->
                int idx = 0
                OperatingSystem.values().each { os ->
                    statement.setTimestamp(++idx, since)
                    List<String> channels = ['commits', 'experiments'].stream()
                        .collect { channel -> "${channel}${os.channelSuffix}-master".toString() }
                    statement.setString(++idx, channels.get(0))
                    statement.setString(++idx, channels.get(1))
                }
                statement.executeQuery().withCloseable { experimentTimes ->
                    while (experimentTimes.next()) {
                        int resultIdx = 0
                        String testClass = experimentTimes.getString(++resultIdx)
                        String testName = experimentTimes.getString(++resultIdx)
                        String testProject = experimentTimes.getString(++resultIdx)
                        OperatingSystem os = OperatingSystem.valueOf(experimentTimes.getString(++resultIdx))
                        long startTime = experimentTimes.getTimestamp(++resultIdx).getTime()
                        long endTime = experimentTimes.getTimestamp(++resultIdx).getTime()
                        if (testProject != null && testClass != null) {
                            PerformanceExperiment performanceExperiment = new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testName))
                            builder.put(new PerformanceExperimentOnOs(performanceExperiment, os), endTime - startTime)
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

    protected <RESULT> RESULT withConnectionClosingDb(String actionName, ConnectionAction<RESULT> action) {
        try {
            return db.withConnection(actionName, action)
        } finally {
            db.close()
        }
    }


    @Override
    void close() {
        db.close()
    }
}
