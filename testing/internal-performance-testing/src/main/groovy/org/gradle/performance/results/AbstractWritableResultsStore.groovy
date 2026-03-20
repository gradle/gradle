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

import java.sql.PreparedStatement
import java.sql.SQLException
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
           ${ OperatingSystem.values().collect { os -> """
            select
               testClass,
               testId,
               testProject,
               '${os.name()}' as os,
               avg(TIMESTAMPDIFF(SECOND, testExecution.startTime, testExecution.endTime)) as duration
            from testExecution
           where startTime > ?
             and (channel in (?, ?))
              or channel like ?
             and testProject is not null
           group by testClass, testId, testProject
           """ }.join("UNION") }
         order by testClass, testId, testProject, os

    """

    static String teamcityBuildIdQueryFor(List<String> teamcityBuildIds) {
        return teamcityBuildIds.isEmpty() ? '' : " or teamcitybuildid in (${String.join(',', Collections.nCopies(teamcityBuildIds.size(), '?'))})"
    }

    static String channelPatternQueryFor(List<String> channelPatterns) {
        return String.join(' or ', Collections.nCopies(channelPatterns.size(), "channel like ?"))
    }

    protected static List<String> distinctValues(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values))
    }

    /**
     * Builds a UNION-based history query for testExecution lookups.
     *
     * Why this exists:
     * - The old single-query form used one large OR predicate:
     *   {@code (... channel like ? ... OR teamcitybuildid in (...))}.
     * - On MySQL, that shape can trigger unstable plans (e.g. broad startTime scans).
     * - Splitting into two selective branches and joining with {@code UNION DISTINCT}
     *   gives the optimizer a more predictable path.
     *
     * Query shape produced:
     * - Channel branch (only when {@code channelPatterns} is non-empty):
     *   {@code select <columns> from testExecution ... and (channel like ? or ...)}
     * - Build ID branch (only when {@code teamcityBuildIds} is non-empty):
     *   {@code select <columns> from testExecution ... and teamcitybuildid in (?, ...)}
     * - Branches are combined using {@code union distinct}.
     *
     * Parameter binding contract:
     * - This method only generates SQL text.
     * - Parameter order is defined by {@link #bindHistoryQueryParams}:
     *   1) channel branch fixed params + channel patterns
     *   2) build-id branch fixed params + build IDs
     *   3) any outer query params (e.g. LIMIT) are bound by callers.
     *
     * Edge cases:
     * - If both lists are empty, returns a no-op query
     *   ({@code select ... from testExecution where 1 = 0}) to keep SQL valid.
     */
    protected static String createHistoryFilterUnionSql(String selectColumns, List<String> channelPatterns, List<String> teamcityBuildIds) {
        String baseSqlTemplate = """
            select %s
            from testExecution
            where testClass = ?
              and testId = ?
              and testProject = ?
              and startTime >= ?
              and %s
            """
        List<String> branches = new ArrayList<>(2)
        if (!channelPatterns.isEmpty()) {
            branches.add(baseSqlTemplate.formatted(selectColumns, "(${channelPatternQueryFor(channelPatterns)})"))
        }
        if (!teamcityBuildIds.isEmpty()) {
            String teamCityBuildIdInClause = "teamcitybuildid in (${String.join(',', Collections.nCopies(teamcityBuildIds.size(), '?'))})"
            branches.add(baseSqlTemplate.formatted(selectColumns, teamCityBuildIdInClause))
        }
        if (branches.isEmpty()) {
            return """
                select ${selectColumns}
                from testExecution
                where 1 = 0
                """
        }
        return String.join(" union distinct ", branches)
    }

    protected static void bindHistoryQueryParams(
        PreparedStatement statement,
        PerformanceExperiment experiment,
        Timestamp minDate,
        List<String> channelPatterns,
        List<String> teamcityBuildIds,
        int mostRecentN
    ) throws SQLException {
        int idx = 0
        if (!channelPatterns.isEmpty()) {
            statement.setString(++idx, experiment.getScenario().getClassName())
            statement.setString(++idx, experiment.getScenario().getTestName())
            statement.setString(++idx, experiment.getTestProject())
            statement.setTimestamp(++idx, minDate)
            for (String channelPattern : channelPatterns) {
                statement.setString(++idx, channelPattern)
            }
        }
        if (!teamcityBuildIds.isEmpty()) {
            statement.setString(++idx, experiment.getScenario().getClassName())
            statement.setString(++idx, experiment.getScenario().getTestName())
            statement.setString(++idx, experiment.getTestProject())
            statement.setTimestamp(++idx, minDate)
            for (String teamcityBuildId : teamcityBuildIds) {
                statement.setString(++idx, teamcityBuildId)
            }
        }
        statement.setInt(++idx, mostRecentN)
    }

    protected static List<Object> createHistoryQueryParams(
        PerformanceExperiment experiment,
        Timestamp minDate,
        List<String> channelPatterns,
        List<String> teamcityBuildIds,
        int mostRecentN
    ) {
        List<Object> params = new ArrayList<>(5 + channelPatterns.size() + teamcityBuildIds.size())
        params.add(experiment.getScenario().getClassName())
        params.add(experiment.getScenario().getTestName())
        params.add(experiment.getTestProject())
        params.add(minDate)
        params.addAll(channelPatterns)
        params.addAll(teamcityBuildIds)
        params.add(mostRecentN)
        return params
    }

    protected static Boolean toNullableBoolean(Object value) {
        if (value == null) {
            return null
        }
        if (value instanceof Boolean) {
            return (Boolean) value
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0
        }
        return Boolean.valueOf(value.toString())
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
                    statement.setString(++idx, "commits${os.channelSuffix}-gh-readonly-queue/master/%")
                }
                statement.executeQuery().withCloseable { experimentTimes ->
                    while (experimentTimes.next()) {
                        int resultIdx = 0
                        String testClass = experimentTimes.getString(++resultIdx)
                        String testName = experimentTimes.getString(++resultIdx)
                        String testProject = experimentTimes.getString(++resultIdx)
                        OperatingSystem os = OperatingSystem.valueOf(experimentTimes.getString(++resultIdx))
                        long avgDuration = experimentTimes.getLong(++resultIdx)
                        if (testProject != null && testClass != null) {
                            PerformanceExperiment performanceExperiment = new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testName))
                            builder.put(new PerformanceExperimentOnOs(performanceExperiment, os), avgDuration)
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
