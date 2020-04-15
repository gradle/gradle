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
import org.gradle.internal.UncheckedException;
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
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import static org.gradle.performance.results.ResultsStoreHelper.toArray;

/**
 * A {@link DataReporter} implementation that stores results in an H2 relational database.
 */
public class CrossVersionResultsStore implements DataReporter<CrossVersionPerformanceResults>, ResultsStore {
    private static final String FLAKINESS_RATE_SQL =
        "SELECT TESTID, AVG(CONVERT(CASEWHEN(DIFFCONFIDENCE > 0.97, 1, 0), DECIMAL)) AS FAILURE_RATE\n" +
            "FROM TESTEXECUTION\n" +
            "WHERE (CHANNEL = 'flakiness-detection-master' OR CHANNEL = 'flakiness-detection-release') AND STARTTIME>?\n" +
            "GROUP BY TESTID";
    private static final String FAILURE_THRESOLD_SQL =
        "SELECT TESTID, MAX(ABS((BASELINEMEDIAN-CURRENTMEDIAN)/BASELINEMEDIAN)) as THRESHOLD\n" +
            "FROM TESTEXECUTION\n" +
            "WHERE (CHANNEL = 'flakiness-detection-master' or CHANNEL= 'flakiness-detection-release') AND STARTTIME > ? AND DIFFCONFIDENCE > 0.97\n" +
            "GROUP BY TESTID";
    // Only the flakiness detection results within 90 days will be considered.
    private static final int FLAKINESS_DETECTION_DAYS = 90;
    private final long ignoreV17Before;
    private final PerformanceDatabase db;
    private final Map<String, GradleVersion> gradleVersionCache = new HashMap<>();

    public CrossVersionResultsStore() {
        this("results");
    }

    public CrossVersionResultsStore(String databaseName) {
        db = new PerformanceDatabase(databaseName, new CrossVersionResultsSchemaInitializer(), new StaleDataCleanupInitializer());

        // Ignore some broken samples before the given date
        DateFormat timeStampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        timeStampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            ignoreV17Before = timeStampFormat.parse("2013-07-03 00:00:00").getTime();
        } catch (ParseException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void report(final CrossVersionPerformanceResults results) {
        try {
            db.withConnection((ConnectionAction<Void>) connection -> {
                long testId = insertExecution(connection, results);
                batchInsertOperation(connection, results, testId);
                updatePreviousTestId(connection, results);
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not open results datastore '%s'.", db.getUrl()), e);
        }
    }

    private void updatePreviousTestId(Connection connection, CrossVersionPerformanceResults results) throws SQLException {
        for (String previousId : results.getPreviousTestIds()) {
            try (PreparedStatement statement = connection.prepareStatement("update testExecution set testId = ? where testId = ?")) {
                statement.setString(1, results.getTestId());
                statement.setString(2, previousId);
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
            "testId", "startTime", "endTime", "targetVersion", "testProject", "tasks", "args", "gradleOpts", "daemon", "operatingSystem",
            "jvm", "vcsBranch", "vcsCommit", "channel", "host", "cleanTasks", "teamCityBuildId", "currentMedian", "baselineMedian", "diffConfidence");


        try (PreparedStatement statement = connection.prepareStatement(insertStatement)) {
            statement.setString(1, results.getTestId());
            statement.setTimestamp(2, new Timestamp(results.getStartTime()));
            statement.setTimestamp(3, new Timestamp(results.getEndTime()));
            statement.setString(4, results.getVersionUnderTest());
            statement.setString(5, results.getTestProject());
            statement.setObject(6, toArray(results.getTasks()));
            statement.setObject(7, toArray(results.getArgs()));
            statement.setObject(8, toArray(results.getGradleOpts()));
            statement.setObject(9, results.getDaemon());
            statement.setString(10, results.getOperatingSystem());
            statement.setString(11, results.getJvm());
            statement.setString(12, results.getVcsBranch());
            String vcs = results.getVcsCommits() == null ? null : Joiner.on(",").join(results.getVcsCommits());
            statement.setString(13, vcs);
            statement.setString(14, results.getChannel());
            statement.setString(15, results.getHost());
            statement.setObject(16, toArray(results.getCleanTasks()));
            statement.setString(17, results.getTeamCityBuildId());

            if (results.getBaselineVersions().size() == 1) {
                MeasuredOperationList current = results.getCurrent();
                MeasuredOperationList baseline = results.getBaselineVersions().iterator().next().getResults();

                BigDecimal currentMedian = current.getTotalTime().getMedian().toUnits(Duration.MILLI_SECONDS).getValue();
                BigDecimal baselineMedian = baseline.getTotalTime().getMedian().toUnits(Duration.MILLI_SECONDS).getValue();
                BigDecimal diffConfidence = new BigDecimal(DataSeries.confidenceInDifference(current.getTotalTime(), baseline.getTotalTime()));
                statement.setBigDecimal(18, currentMedian);
                statement.setBigDecimal(19, baselineMedian);
                statement.setBigDecimal(20, diffConfidence);
            } else {
                statement.setBigDecimal(18, null);
                statement.setBigDecimal(19, null);
                statement.setBigDecimal(20, null);
            }

            statement.execute();
            ResultSet keys = null;
            try {
                keys = statement.getGeneratedKeys();
                keys.next();
                return keys.getLong(1);
            } finally {
                closeResultSet(keys);
            }
        }
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
    public List<String> getTestNames() {
        try {
            return db.withConnection(connection -> {
                List<String> testNames = new ArrayList<>();
                Statement statement = null;
                ResultSet testExecutions = null;

                try {
                    statement = connection.createStatement();
                    testExecutions = statement.executeQuery("select distinct testId from testExecution order by testId");
                    while (testExecutions.next()) {
                        testNames.add(testExecutions.getString(1));
                    }
                } finally {
                    closeStatement(statement);
                    closeResultSet(testExecutions);
                }

                return testNames;
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load test history from datastore '%s'.", db.getUrl()), e);
        }
    }

    @Override
    public CrossVersionPerformanceTestHistory getTestResults(String testName, String channel) {
        return getTestResults(testName, Integer.MAX_VALUE, Integer.MAX_VALUE, channel);
    }


    @Override
    public CrossVersionPerformanceTestHistory getTestResults(final String testName, final int mostRecentN, final int maxDaysOld, final String channel) {
        try {
            return db.withConnection(connection -> {
                Map<Long, CrossVersionPerformanceResults> results = Maps.newLinkedHashMap();
                Set<String> allVersions = new TreeSet<>(Comparator.comparing(this::resolveGradleVersion));
                Set<String> allBranches = new TreeSet<>();

                PreparedStatement executionsForName = null;
                PreparedStatement operationsForExecution = null;
                ResultSet testExecutions = null;
                ResultSet operations = null;

                try {
                    executionsForName = connection.prepareStatement("select top ? id, startTime, endTime, targetVersion, testProject, tasks, args, gradleOpts, daemon, operatingSystem, jvm, vcsBranch, vcsCommit, channel, host, cleanTasks, teamCityBuildId from testExecution where testId = ? and startTime >= ? and channel = ? order by startTime desc");
                    executionsForName.setFetchSize(mostRecentN);
                    executionsForName.setInt(1, mostRecentN);
                    executionsForName.setString(2, testName);
                    Timestamp minDate = new Timestamp(LocalDate.now().minusDays(maxDaysOld).toDate().getTime());
                    executionsForName.setTimestamp(3, minDate);
                    executionsForName.setString(4, channel);

                    testExecutions = executionsForName.executeQuery();
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        CrossVersionPerformanceResults performanceResults = new CrossVersionPerformanceResults();
                        performanceResults.setTestId(testName);
                        performanceResults.setStartTime(testExecutions.getTimestamp(2).getTime());
                        performanceResults.setEndTime(testExecutions.getTimestamp(3).getTime());
                        performanceResults.setVersionUnderTest(testExecutions.getString(4));
                        performanceResults.setTestProject(testExecutions.getString(5));
                        performanceResults.setTasks(ResultsStoreHelper.toList(testExecutions.getObject(6)));
                        performanceResults.setArgs(ResultsStoreHelper.toList(testExecutions.getObject(7)));
                        performanceResults.setGradleOpts(ResultsStoreHelper.toList(testExecutions.getObject(8)));
                        performanceResults.setDaemon((Boolean) testExecutions.getObject(9));
                        performanceResults.setOperatingSystem(testExecutions.getString(10));
                        performanceResults.setJvm(testExecutions.getString(11));
                        performanceResults.setVcsBranch(testExecutions.getString(12).trim());
                        performanceResults.setVcsCommits(ResultsStoreHelper.split(testExecutions.getString(13)));
                        performanceResults.setChannel(testExecutions.getString(14));
                        performanceResults.setHost(testExecutions.getString(15));
                        performanceResults.setCleanTasks(ResultsStoreHelper.toList(testExecutions.getObject(16)));
                        performanceResults.setTeamCityBuildId(testExecutions.getString(17));

                        results.put(id, performanceResults);
                        allBranches.add(performanceResults.getVcsBranch());
                    }

                    operationsForExecution = connection.prepareStatement("select version, testExecution, totalTime from testOperation "
                        + "where testExecution in (select top ? id from testExecution where testId = ? and startTime >= ? and channel = ? order by startTime desc)");
                    operationsForExecution.setFetchSize(10 * results.size());
                    operationsForExecution.setInt(1, mostRecentN);
                    operationsForExecution.setString(2, testName);
                    operationsForExecution.setTimestamp(3, minDate);
                    operationsForExecution.setString(4, channel);

                    operations = operationsForExecution.executeQuery();
                    while (operations.next()) {
                        CrossVersionPerformanceResults result = results.get(operations.getLong(2));
                        if (result == null) {
                            continue;
                        }
                        String version = operations.getString(1);
                        if ("1.7".equals(version) && result.getStartTime() <= ignoreV17Before) {
                            // Ignore some broken samples
                            continue;
                        }
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

                } finally {
                    closeResultSet(operations);
                    closeStatement(operationsForExecution);
                    closeResultSet(testExecutions);
                    closeStatement(executionsForName);
                }

                return new CrossVersionPerformanceTestHistory(testName, new ArrayList<>(allVersions), new ArrayList<>(allBranches), Lists.newArrayList(results.values()));
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load results from datastore '%s'.", db.getUrl()), e);
        }
    }

    protected GradleVersion resolveGradleVersion(String version) {
        GradleVersion gradleVersion = gradleVersionCache.get(version);
        if (gradleVersion == null) {
            gradleVersion = GradleVersion.version(version);
            gradleVersionCache.put(version, gradleVersion);
        }
        return gradleVersion;
    }

    @Override
    public void close() {
        db.close();
    }

    private class CrossVersionResultsSchemaInitializer implements ConnectionAction<Void> {
        @Override
        public Void execute(Connection connection) throws SQLException {
            Statement statement = null;

            try {
                statement = connection.createStatement();
                statement.execute("create table if not exists testExecution (id bigint identity not null, testId varchar not null, startTime timestamp not null, targetVersion varchar not null, testProject varchar not null, tasks array not null, args array not null, operatingSystem varchar not null, jvm varchar not null)");
                statement.execute("create table if not exists testOperation (testExecution bigint not null, version varchar, totalTime decimal not null, foreign key(testExecution) references testExecution(id))");
                statement.execute("alter table testExecution add column if not exists vcsBranch varchar not null default 'master'");
                statement.execute("alter table testExecution add column if not exists vcsCommit varchar");
                statement.execute("alter table testExecution add column if not exists gradleOpts array");
                statement.execute("alter table testExecution add column if not exists daemon boolean");
                if (DataBaseSchemaUtil.columnExists(connection, "TESTOPERATION", "EXECUTIONTIMEMS")) {
                    statement.execute("alter table testOperation alter column executionTimeMs rename to totalTime");
                }
                if (DataBaseSchemaUtil.columnExists(connection, "TESTEXECUTION", "EXECUTIONTIME")) {
                    statement.execute("alter table testExecution alter column executionTime rename to startTime");
                }
                if (!DataBaseSchemaUtil.columnExists(connection, "TESTEXECUTION", "ENDTIME")) {
                    statement.execute("alter table testExecution add column endTime timestamp");
                    statement.execute("update testExecution set endTime = startTime");
                    statement.execute("alter table testExecution alter column endTime set not null");
                }
                if (!DataBaseSchemaUtil.columnExists(connection, "TESTEXECUTION", "CHANNEL")) {
                    statement.execute("alter table testExecution add column if not exists channel varchar");
                    statement.execute("update testExecution set channel='commits'");
                    statement.execute("alter table testExecution alter column channel set not null");
                    statement.execute("create index if not exists testExecution_channel on testExecution (channel)");
                }

                addColumnToExecutionTableIfNotExists(connection, statement, "HOST", "varchar");
                addColumnToExecutionTableIfNotExists(connection, statement, "teamCityBuildId", "varchar");
                addColumnToExecutionTableIfNotExists(connection, statement, "baselineMedian", "decimal");
                addColumnToExecutionTableIfNotExists(connection, statement, "currentMedian", "decimal");
                addColumnToExecutionTableIfNotExists(connection, statement, "diffConfidence", "decimal");

                statement.execute("create index if not exists testExecution_testId on testExecution (testId)");
                statement.execute("create index if not exists testExecution_executionTime on testExecution (startTime desc)");

                if (!DataBaseSchemaUtil.columnExists(connection, "TESTEXECUTION", "CLEANTASKS")) {
                    statement.execute("alter table testExecution add column if not exists cleanTasks array");
                }

                DataBaseSchemaUtil.removeOutdatedColumnsFromTestDB(connection, statement);
            } finally {
                closeStatement(statement);
            }

            return null;
        }
    }

    private void addColumnToExecutionTableIfNotExists(Connection connection, Statement statement, String column, String type) throws SQLException {
        if (!DataBaseSchemaUtil.columnExists(connection, "TESTEXECUTION", column)) {
            statement.execute("alter table testExecution add column if not exists " + column + " " + type);
        }
    }

    private void closeStatement(Statement statement) throws SQLException {
        if (statement != null) {
            statement.close();
        }
    }

    private void closeResultSet(ResultSet resultSet) throws SQLException {
        if (resultSet != null) {
            resultSet.close();
        }
    }

    public Map<String, BigDecimal> getFlakinessRates() {
        Timestamp time = Timestamp.valueOf(LocalDateTime.now().minusDays(FLAKINESS_DETECTION_DAYS));
        return queryFlakinessData(FLAKINESS_RATE_SQL, time);
    }

    public Map<String, BigDecimal> getFailureThresholds() {
        Timestamp time = Timestamp.valueOf(LocalDateTime.now().minusDays(FLAKINESS_DETECTION_DAYS));
        return queryFlakinessData(FAILURE_THRESOLD_SQL, time);
    }

    private PreparedStatement prepareStatement(Connection connection, String sql, Timestamp param) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setTimestamp(1, param);
        return statement;
    }

    private Map<String, BigDecimal> queryFlakinessData(String sql, Timestamp time) {
        try {
            return db.withConnection(connection -> {
                Map<String, BigDecimal> results = Maps.newHashMap();
                try (PreparedStatement statement = prepareStatement(connection, sql, time); ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String scenario = resultSet.getString(1);
                        BigDecimal value = resultSet.getBigDecimal(2);
                        results.put(scenario, value);
                    }
                }
                return results;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
