/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance.results;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.performance.measure.Amount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.util.GradleVersion;
import org.joda.time.LocalDate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.performance.results.ResultsStoreHelper.toArray;

/**
 * A {@link DataReporter} implementation that stores results in an H2 relational database.
 */
public class CrossVersionResultsStore extends AbstractWritableResultsStore<CrossVersionPerformanceResults> {
    private static final String FLAKINESS_RATE_SQL =
        "SELECT TESTCLASS, TESTID, TESTPROJECT, AVG(\n" +
            "  CASE WHEN DIFFCONFIDENCE > 0.97 THEN 1.0\n" +
            "    ELSE 0.0\n" +
            "  END) AS FAILURE_RATE \n" +
            "  FROM testExecution\n" +
            " WHERE (CHANNEL = 'flakiness-detection-master' OR CHANNEL = 'flakiness-detection-release') AND STARTTIME> ?\n" +
            "GROUP BY TESTCLASS, TESTID, TESTPROJECT ORDER by FAILURE_RATE;";
    private static final String FAILURE_THRESOLD_SQL =
        "SELECT TESTCLASS, TESTID, TESTPROJECT, MAX(ABS((BASELINEMEDIAN-CURRENTMEDIAN)/BASELINEMEDIAN)) as THRESHOLD\n" +
            "FROM testExecution\n" +
            "WHERE (CHANNEL = 'flakiness-detection-master' or CHANNEL= 'flakiness-detection-release') AND STARTTIME > ? AND DIFFCONFIDENCE > 0.97\n" +
            "GROUP BY TESTCLASS, TESTID, TESTPROJECT";


    // Only the flakiness detection results within 90 days will be considered.
    private static final int FLAKINESS_DETECTION_DAYS = 90;
    private final Map<String, GradleVersion> gradleVersionCache = new HashMap<>();

    public CrossVersionResultsStore() {
        this("results");
    }

    public CrossVersionResultsStore(String databaseName) {
        super(new PerformanceDatabase(databaseName));
    }

    @Override
    public void report(final CrossVersionPerformanceResults results) {
        withConnectionClosingDb("write results", (ConnectionAction<Void>) connection -> {
            long testId = insertExecution(connection, results);
            batchInsertOperation(connection, results, testId);
            updatePreviousTestId(connection, results);
            insertExecutionExperiment(connection, results);
            return null;
        });
    }

    private void insertExecutionExperiment(Connection connection, CrossVersionPerformanceResults results) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into testExecutionExperiment(testId, testProject, testClass) values (?, ?, ?)")) {
            statement.setString(1, results.getTestId());
            statement.setString(2, results.getTestProject());
            statement.setString(3, results.getTestClass());
            statement.execute();
        } catch (SQLIntegrityConstraintViolationException ignore) {
            // This is expected, ignore.
        }
    }

    private void updatePreviousTestId(Connection connection, CrossVersionPerformanceResults results) throws SQLException {
        for (String previousId : results.getPreviousTestIds()) {
            try (PreparedStatement statement = connection.prepareStatement("update testExecution set testId = ? where testId = ? and testProject = ? and testClass = ?")) {
                statement.setString(1, results.getTestId());
                statement.setString(2, previousId);
                statement.setString(3, results.getTestProject());
                statement.setString(4, results.getTestClass());
                statement.execute();
            }
        }
    }

    private void batchInsertOperation(Connection connection, CrossVersionPerformanceResults results, long testId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insertStatement("testOperation", "testExecution", "version", "totalTime"))) {
            addOperations(statement, testId, null, results.getCurrent());
            for (BaselineVersion baselineVersion : results.getBaselineVersions()) {
                addOperations(statement, testId, baselineVersion.getVersion(), baselineVersion.getResults());
            }
            statement.executeBatch();
        }
    }

    private long insertExecution(Connection connection, CrossVersionPerformanceResults results) throws SQLException {
        String insertStatement = insertStatement("testExecution",
            "testClass", "testId", "startTime", "endTime", "targetVersion", "testProject", "tasks", "args", "gradleOpts", "daemon", "operatingSystem",
            "jvm", "vcsBranch", "vcsCommit", "channel", "host", "cleanTasks", "teamCityBuildId", "currentMedian", "baselineMedian", "diffConfidence");


        try (PreparedStatement statement = connection.prepareStatement(insertStatement, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, results.getTestClass());
            statement.setString(2, results.getTestId());
            statement.setTimestamp(3, new Timestamp(results.getStartTime()));
            statement.setTimestamp(4, new Timestamp(results.getEndTime()));
            statement.setString(5, results.getVersionUnderTest());
            statement.setString(6, results.getTestProject());
            statement.setObject(7, toArray(results.getTasks()));
            statement.setObject(8, toArray(results.getArgs()));
            statement.setObject(9, toArray(results.getGradleOpts()));
            statement.setBoolean(10, results.getDaemon());
            statement.setString(11, results.getOperatingSystem());
            statement.setString(12, results.getJvm());
            statement.setString(13, results.getVcsBranch());
            String vcs = results.getVcsCommits() == null ? null : Joiner.on(",").join(results.getVcsCommits());
            statement.setString(14, vcs);
            statement.setString(15, results.getChannel());
            statement.setString(16, results.getHost());
            statement.setObject(17, toArray(results.getCleanTasks()));
            statement.setString(18, results.getTeamCityBuildId());

            if (results.getBaselineVersions().size() == 1) {
                MeasuredOperationList current = results.getCurrent();
                MeasuredOperationList baseline = results.getBaselineVersions().iterator().next().getResults();

                BigDecimal currentMedian = getMedianInMillis(current);
                BigDecimal baselineMedian = getMedianInMillis(baseline);
                BigDecimal diffConfidence = (currentMedian != null && baselineMedian != null) ? BigDecimal.valueOf(DataSeries.confidenceInDifference(current.getTotalTime(), baseline.getTotalTime())) : null;
                statement.setBigDecimal(19, currentMedian);
                statement.setBigDecimal(20, baselineMedian);
                statement.setBigDecimal(21, diffConfidence);
            } else {
                statement.setBigDecimal(19, null);
                statement.setBigDecimal(20, null);
                statement.setBigDecimal(21, null);
            }

            statement.execute();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static BigDecimal getMedianInMillis(MeasuredOperationList operations) {
        Amount<Duration> median = operations.getTotalTime().getMedian();
        return median == null ? null : median.toUnits(Duration.MILLI_SECONDS).getValue();
    }

    private void addOperations(PreparedStatement statement, long testId, String version, MeasuredOperationList operations) throws SQLException {
        for (MeasuredOperation operation : operations) {
            statement.setLong(1, testId);
            statement.setString(2, version);
            statement.setBigDecimal(3, operation.getTotalTime().toUnits(Duration.MILLI_SECONDS).getValue());
            statement.addBatch();
        }
    }

    @Override
    public List<PerformanceExperiment> getPerformanceExperiments() {
        return withConnection("load test history", connection -> {
            try (
                Statement statement = connection.createStatement();
                ResultSet testExecutions = statement.executeQuery(
                    "select testClass, testId, testProject" +
                        "   from testExecutionExperiment" +
                        "  order by testClass, testId, testProject"
                )
            ) {
                List<PerformanceExperiment> testNames = new ArrayList<>();
                while (testExecutions.next()) {
                    String testClass = testExecutions.getString(1);
                    String testId = testExecutions.getString(2);
                    String testProject = testExecutions.getString(3);
                    testNames.add(new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testId)));
                }
                return testNames;
            }
        });
    }

    @Override
    public CrossVersionPerformanceTestHistory getTestResults(
        PerformanceExperiment experiment,
        int mostRecentN,
        int maxDaysOld,
        List<String> channelPatterns,
        List<String> teamcityBuildIds
    ) {
        return withConnection("load results", connection -> {
            String buildIdQuery = teamcityBuildIdQueryFor(teamcityBuildIds);
            String channelPatternQuery = channelPatternQueryFor(channelPatterns);
            try (
                PreparedStatement executionsForName = connection.prepareStatement("select id, startTime, endTime, targetVersion, tasks, args, gradleOpts, daemon, operatingSystem, jvm, vcsBranch, vcsCommit, channel, host, cleanTasks, teamCityBuildId from testExecution where testClass = ? and testId = ? and testProject = ? and startTime >= ? and (" + channelPatternQuery + buildIdQuery + ") order by startTime desc limit ?");
                PreparedStatement operationsForExecution = connection.prepareStatement("select version, testExecution, totalTime from testOperation "
                    + "where testExecution in (select t.* from ( select id from testExecution where testClass = ? and testId = ? and testProject = ? and startTime >= ? and (" + channelPatternQuery + buildIdQuery + ") order by startTime desc limit ?) as t)")
            ) {
                Map<Long, CrossVersionPerformanceResults> results = Maps.newLinkedHashMap();
                Set<String> allVersions = new TreeSet<>(Comparator.comparing(this::resolveGradleVersion));
                Set<String> allBranches = new TreeSet<>();

                int idx = 0;
                executionsForName.setFetchSize(mostRecentN);
                executionsForName.setString(++idx, experiment.getScenario().getClassName());
                executionsForName.setString(++idx, experiment.getScenario().getTestName());
                executionsForName.setString(++idx, experiment.getTestProject());
                Timestamp minDate = new Timestamp(LocalDate.now().minusDays(maxDaysOld).toDate().getTime());
                executionsForName.setTimestamp(++idx, minDate);
                for (String channelPattern : channelPatterns) {
                    executionsForName.setString(++idx, channelPattern);
                }
                for (String teamcityBuildId : teamcityBuildIds) {
                    executionsForName.setString(++idx, teamcityBuildId);
                }
                executionsForName.setInt(++idx, mostRecentN);

                try (ResultSet testExecutions = executionsForName.executeQuery()) {
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        CrossVersionPerformanceResults performanceResults = new CrossVersionPerformanceResults();
                        performanceResults.setTestClass(experiment.getScenario().getClassName());
                        performanceResults.setTestId(experiment.getScenario().getTestName());
                        performanceResults.setTestProject(experiment.getTestProject());
                        performanceResults.setStartTime(testExecutions.getTimestamp(2).getTime());
                        performanceResults.setEndTime(testExecutions.getTimestamp(3).getTime());
                        performanceResults.setVersionUnderTest(testExecutions.getString(4));
                        performanceResults.setTasks(ResultsStoreHelper.toList(testExecutions.getObject(5)));
                        performanceResults.setArgs(ResultsStoreHelper.toList(testExecutions.getObject(6)));
                        performanceResults.setGradleOpts(ResultsStoreHelper.toList(testExecutions.getObject(7)));
                        performanceResults.setDaemon((Boolean) testExecutions.getObject(8));
                        performanceResults.setOperatingSystem(testExecutions.getString(9));
                        performanceResults.setJvm(testExecutions.getString(10));
                        performanceResults.setVcsBranch(mapVcsBranch(channelPatterns.get(0), testExecutions.getString(11).trim()));
                        performanceResults.setVcsCommits(ResultsStoreHelper.split(testExecutions.getString(12)));
                        performanceResults.setChannel(testExecutions.getString(13));
                        performanceResults.setHost(testExecutions.getString(14));
                        performanceResults.setCleanTasks(ResultsStoreHelper.toList(testExecutions.getObject(15)));
                        performanceResults.setTeamCityBuildId(testExecutions.getString(16));

                        results.put(id, performanceResults);
                        allBranches.add(performanceResults.getVcsBranch());
                    }
                }

                operationsForExecution.setFetchSize(10 * results.size());
                idx = 0;
                operationsForExecution.setString(++idx, experiment.getScenario().getClassName());
                operationsForExecution.setString(++idx, experiment.getScenario().getTestName());
                operationsForExecution.setString(++idx, experiment.getTestProject());
                operationsForExecution.setTimestamp(++idx, minDate);
                for (String channelPattern : channelPatterns) {
                    operationsForExecution.setString(++idx, channelPattern);
                }
                for (String teamcityBuildId : teamcityBuildIds) {
                    operationsForExecution.setString(++idx, teamcityBuildId);
                }
                operationsForExecution.setInt(++idx, mostRecentN);

                try (ResultSet operations = operationsForExecution.executeQuery()) {
                    while (operations.next()) {
                        CrossVersionPerformanceResults result = results.get(operations.getLong(2));
                        if (result == null) {
                            continue;
                        }
                        String version = operations.getString(1);
                        MeasuredOperation operation = new MeasuredOperation();
                        operation.setTotalTime(Duration.millis(operations.getBigDecimal(3)));

                        if (version == null) {
                            result.getCurrent().add(operation);
                        } else {
                            BaselineVersion baselineVersion = result.baseline(version);
                            baselineVersion.getResults().add(operation);
                            allVersions.add(version);
                        }
                    }
                }
                return new CrossVersionPerformanceTestHistory(experiment, new ArrayList<>(allVersions), new ArrayList<>(allBranches), Lists.newArrayList(results.values()));
            }
        });
    }

    private String mapVcsBranch(String channelPattern, String vcsBranch) {
        if (!channelPattern.startsWith("commits-")) {
            return vcsBranch;
        }
        String currentBranch = channelPattern.substring("commits-".length());
        if (currentBranch.equals(vcsBranch)) {
            return currentBranch;
        }
        if (vcsBranch.startsWith("pre-test/")) {
            Matcher matcher = Pattern.compile("pre-test/([^/]*)/.*").matcher(vcsBranch);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return vcsBranch;
    }

    protected GradleVersion resolveGradleVersion(String version) {
        GradleVersion gradleVersion = gradleVersionCache.get(version);
        if (gradleVersion == null) {
            gradleVersion = GradleVersion.version(version);
            gradleVersionCache.put(version, gradleVersion);
        }
        return gradleVersion;
    }

    public Map<PerformanceExperiment, BigDecimal> getFlakinessRates() {
        Timestamp time = Timestamp.valueOf(LocalDateTime.now().minusDays(FLAKINESS_DETECTION_DAYS));
        return queryFlakinessData(FLAKINESS_RATE_SQL, time);
    }

    public Map<PerformanceExperiment, BigDecimal> getFailureThresholds() {
        Timestamp time = Timestamp.valueOf(LocalDateTime.now().minusDays(FLAKINESS_DETECTION_DAYS));
        return queryFlakinessData(FAILURE_THRESOLD_SQL, time);
    }

    private PreparedStatement prepareStatement(Connection connection, String sql, Timestamp param) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setTimestamp(1, param);
        return statement;
    }

    private Map<PerformanceExperiment, BigDecimal> queryFlakinessData(String sql, Timestamp time) {
        return withConnection("query flakiness data", connection -> {
            Map<PerformanceExperiment, BigDecimal> results = Maps.newHashMap();
            try (
                PreparedStatement statement = prepareStatement(connection, sql, time);
                ResultSet resultSet = statement.executeQuery()
            ) {
                while (resultSet.next()) {
                    String testClass = resultSet.getString(1);
                    String testName = resultSet.getString(2);
                    String testProject = resultSet.getString(3);
                    BigDecimal value = resultSet.getBigDecimal(4);
                    results.put(new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testName)), value);
                }
            }
            return results;
        });
    }
}
