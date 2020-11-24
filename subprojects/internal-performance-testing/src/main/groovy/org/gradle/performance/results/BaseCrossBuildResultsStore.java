/*
 * Copyright 2015 the original author or authors.
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.joda.time.LocalDate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.gradle.performance.results.ResultsStoreHelper.split;
import static org.gradle.performance.results.ResultsStoreHelper.toArray;
import static org.gradle.performance.results.ResultsStoreHelper.toList;

public class BaseCrossBuildResultsStore<R extends CrossBuildPerformanceResults> extends AbstractWritableResultsStore<R> {

    private final String resultType;

    public BaseCrossBuildResultsStore(String resultType) {
        super(new PerformanceDatabase("cross_build_results"));
        this.resultType = resultType;
    }

    @Override
    public void report(final R results) {
        withConnectionClosingDb("write results", (ConnectionAction<Void>) connection -> {
            long executionId = insertExecution(connection, results);
            batchInsertOperation(connection, results, executionId);
            insertExecutionExperiment(connection, results);
            return null;
        });
    }

    private void batchInsertOperation(Connection connection, R results, long executionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into testOperation(testExecution, displayName, tasks, args, gradleOpts, daemon, totalTime, cleanTasks) values (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (BuildDisplayInfo displayInfo : results.getBuilds()) {
                addOperations(statement, executionId, displayInfo, results.buildResult(displayInfo));
            }
            statement.executeBatch();
        }
    }

    private long insertExecution(Connection connection, R results) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into testExecution(testClass, testId, testProject, startTime, endTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup, resultType, channel, host, teamCityBuildId) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, results.getTestClass());
            statement.setString(2, results.getTestId());
            statement.setString(3, results.getTestProject());
            statement.setTimestamp(4, new Timestamp(results.getStartTime()));
            statement.setTimestamp(5, new Timestamp(results.getEndTime()));
            statement.setString(6, results.getVersionUnderTest());
            statement.setString(7, results.getOperatingSystem());
            statement.setString(8, results.getJvm());
            statement.setString(9, results.getVcsBranch());
            statement.setString(10, Joiner.on(",").join(results.getVcsCommits()));
            statement.setString(11, results.getTestGroup());
            statement.setString(12, resultType);
            statement.setString(13, results.getChannel());
            statement.setString(14, results.getHost());
            statement.setString(15, results.getTeamCityBuildId());
            statement.execute();
            ResultSet keys = statement.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private void insertExecutionExperiment(Connection connection, R results) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into testExecutionExperiment(testId, testProject, testClass, resultType) values (?, ?, ?, ?)")) {
            statement.setString(1, results.getTestId());
            statement.setString(2, results.getTestProject());
            statement.setString(3, results.getTestClass());
            statement.setString(4, resultType);
            statement.execute();
        } catch (SQLIntegrityConstraintViolationException ignore) {
            // This is expected, ignore.
        }
    }

    private void addOperations(PreparedStatement statement, long executionId, BuildDisplayInfo displayInfo, MeasuredOperationList operations) throws SQLException {
        for (MeasuredOperation operation : operations) {
            statement.setLong(1, executionId);
            statement.setString(2, displayInfo.getDisplayName());
            statement.setObject(3, toArray(displayInfo.getTasksToRun()));
            statement.setObject(4, toArray(displayInfo.getArgs()));
            statement.setObject(5, toArray(displayInfo.getGradleOpts()));
            statement.setBoolean(6, displayInfo.getDaemon());
            statement.setBigDecimal(7, operation.getTotalTime().toUnits(Duration.MILLI_SECONDS).getValue());
            statement.setObject(8, toArray(displayInfo.getCleanTasks()));
            statement.addBatch();
        }
    }

    @Override
    public List<PerformanceExperiment> getPerformanceExperiments() {
        return withConnection("load test history", connection -> {
            Set<PerformanceExperiment> testNames = Sets.newLinkedHashSet();
            try (PreparedStatement testIdsStatement = connection.prepareStatement(
                "select testClass, testId, testProject" +
                    "   from testExecutionExperiment" +
                    "  where resultType = ?" +
                    "  order by testClass, testId, testProject")
            ) {
                testIdsStatement.setString(1, resultType);
                try (ResultSet testExecutions = testIdsStatement.executeQuery()) {
                    while (testExecutions.next()) {
                        String testClass = testExecutions.getString(1);
                        String testName = testExecutions.getString(2);
                        String testProject = testExecutions.getString(3);
                        if (testProject != null && testClass != null) {
                            testNames.add(new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testName)));
                        }
                    }
                    return Lists.newArrayList(testNames);
                }
            }
        });
    }

    @Override
    public CrossBuildPerformanceTestHistory getTestResults(PerformanceExperiment experiment, String channel) {
        return getTestResults(experiment, Integer.MAX_VALUE, Integer.MAX_VALUE, channel);
    }

    @Override
    public CrossBuildPerformanceTestHistory getTestResults(final PerformanceExperiment experiment, final int mostRecentN, final int maxDaysOld, final String channel) {
        return withConnection("load results", connection -> {
            try (
                PreparedStatement executionsForName = connection.prepareStatement("select id, startTime, endTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup, channel, host, teamCityBuildId from testExecution where testClass = ? and testId = ? and testProject = ? and startTime >= ? and channel = ? order by startTime desc limit ?");
                PreparedStatement operationsForExecution = connection.prepareStatement("select displayName, tasks, args, gradleOpts, daemon, totalTime, cleanTasks from testOperation where testExecution = ?")
            ) {
                executionsForName.setString(1, experiment.getScenario().getClassName());
                executionsForName.setString(2, experiment.getScenario().getTestName());
                executionsForName.setString(3, experiment.getTestProject());
                Timestamp minDate = new Timestamp(LocalDate.now().minusDays(maxDaysOld).toDate().getTime());
                executionsForName.setTimestamp(4, minDate);
                executionsForName.setString(5, channel);
                executionsForName.setInt(6, mostRecentN);
                try (ResultSet testExecutions = executionsForName.executeQuery()) {
                    List<CrossBuildPerformanceResults> results = Lists.newArrayList();
                    Set<BuildDisplayInfo> builds = Sets.newTreeSet(Comparator.comparing(BuildDisplayInfo::getDisplayName));
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        CrossBuildPerformanceResults performanceResults = new CrossBuildPerformanceResults();
                        performanceResults.setTestClass(experiment.getScenario().getClassName());
                        performanceResults.setTestId(experiment.getScenario().getTestName());
                        performanceResults.setTestProject(experiment.getTestProject());
                        performanceResults.setStartTime(testExecutions.getTimestamp(2).getTime());
                        performanceResults.setEndTime(testExecutions.getTimestamp(3).getTime());
                        performanceResults.setVersionUnderTest(testExecutions.getString(4));
                        performanceResults.setOperatingSystem(testExecutions.getString(5));
                        performanceResults.setJvm(testExecutions.getString(6));
                        performanceResults.setVcsBranch(testExecutions.getString(7).trim());
                        performanceResults.setVcsCommits(split(testExecutions.getString(8)));
                        performanceResults.setTestGroup(testExecutions.getString(9));
                        performanceResults.setChannel(testExecutions.getString(10));
                        performanceResults.setHost(testExecutions.getString(11));
                        performanceResults.setTeamCityBuildId(testExecutions.getString(12));

                        if (ignore(performanceResults)) {
                            continue;
                        }

                        results.add(performanceResults);

                        operationsForExecution.setLong(1, id);
                        try (ResultSet resultSet = operationsForExecution.executeQuery()) {
                            while (resultSet.next()) {
                                String displayName = resultSet.getString(1);
                                List<String> tasksToRun = toList(resultSet.getObject(2));
                                List<String> cleanTasks = toList(resultSet.getObject(7));
                                List<String> args = toList(resultSet.getObject(3));
                                List<String> gradleOpts = toList(resultSet.getObject(4));
                                Boolean daemon = (Boolean) resultSet.getObject(5);
                                BuildDisplayInfo displayInfo = new BuildDisplayInfo(experiment.getTestProject(), displayName, tasksToRun, cleanTasks, args, gradleOpts, daemon);

                                MeasuredOperation operation = new MeasuredOperation();
                                operation.setTotalTime(Duration.millis(resultSet.getBigDecimal(6)));
                                performanceResults.buildResult(displayInfo).add(operation);
                                builds.add(displayInfo);
                            }
                        }
                    }
                    return new CrossBuildPerformanceTestHistory(experiment, ImmutableList.copyOf(builds), results);
                }
            }
        });
    }

    protected boolean ignore(CrossBuildPerformanceResults performanceResults) {
        return false;
    }
}
