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

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.gradle.performance.results.ResultsStoreHelper.split;
import static org.gradle.performance.results.ResultsStoreHelper.toArray;
import static org.gradle.performance.results.ResultsStoreHelper.toList;

public class BaseCrossBuildResultsStore<R extends CrossBuildPerformanceResults> implements WritableResultsStore<R>, Closeable {

    private final PerformanceDatabase db;
    private final String resultType;

    public BaseCrossBuildResultsStore(String resultType) {
        this.db = new PerformanceDatabase("cross_build_results");
        this.resultType = resultType;
    }

    @Override
    public void report(final R results) {
        try {
            db.withConnection((ConnectionAction<Void>) connection -> {
                long executionId;
                PreparedStatement statement = connection.prepareStatement("insert into testExecution(testId, testProject, startTime, endTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup, resultType, channel, host, teamCityBuildId) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                try {
                    statement.setString(1, results.getTestId());
                    statement.setString(2, results.getTestProject());
                    statement.setTimestamp(3, new Timestamp(results.getStartTime()));
                    statement.setTimestamp(4, new Timestamp(results.getEndTime()));
                    statement.setString(5, results.getVersionUnderTest());
                    statement.setString(6, results.getOperatingSystem());
                    statement.setString(7, results.getJvm());
                    statement.setString(8, results.getVcsBranch());
                    statement.setString(9, Joiner.on(",").join(results.getVcsCommits()));
                    statement.setString(10, results.getTestGroup());
                    statement.setString(11, resultType);
                    statement.setString(12, results.getChannel());
                    statement.setString(13, results.getHost());
                    statement.setString(14, results.getTeamCityBuildId());
                    statement.execute();
                    ResultSet keys = statement.getGeneratedKeys();
                    keys.next();
                    executionId = keys.getLong(1);
                } finally {
                    statement.close();
                }
                statement = connection.prepareStatement("insert into testOperation(testExecution, displayName, tasks, args, gradleOpts, daemon, totalTime, cleanTasks) values (?, ?, ?, ?, ?, ?, ?, ?)");
                try {
                    for (BuildDisplayInfo displayInfo : results.getBuilds()) {
                        addOperations(statement, executionId, displayInfo, results.buildResult(displayInfo));
                    }
                    statement.executeBatch();
                } finally {
                    statement.close();
                }
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not open results datastore '%s'.", db.getUrl()), e);
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
    public void close() {
        db.close();
    }

    @Override
    public List<PerformanceExperiment> getPerformanceExperiments() {
        try {
            return db.withConnection((ConnectionAction<List<PerformanceExperiment>>) connection -> {
                Set<PerformanceExperiment> testNames = Sets.newLinkedHashSet();
                PreparedStatement testIdsStatement = connection.prepareStatement("select distinct testId, testProject from testExecution where resultType = ? order by testId, testProject");
                testIdsStatement.setString(1, resultType);
                ResultSet testExecutions = testIdsStatement.executeQuery();
                while (testExecutions.next()) {
                    // TODO: Add testProject to cross_build_results DB
                    testNames.add(new PerformanceExperiment(testExecutions.getString(2), testExecutions.getString(1)));
                }
                testExecutions.close();
                testIdsStatement.close();
                return Lists.newArrayList(testNames);
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load test history from datastore '%s'.", db.getUrl()), e);
        }
    }

    @Override
    public CrossBuildPerformanceTestHistory getTestResults(PerformanceExperiment experiment, String channel) {
        return getTestResults(experiment, Integer.MAX_VALUE, Integer.MAX_VALUE, channel);
    }

    @Override
    public CrossBuildPerformanceTestHistory getTestResults(final PerformanceExperiment experiment, final int mostRecentN, final int maxDaysOld, final String channel) {
        try {
            return db.withConnection(connection -> {
                List<CrossBuildPerformanceResults> results = Lists.newArrayList();
                Set<BuildDisplayInfo> builds = Sets.newTreeSet(Comparator.comparing(BuildDisplayInfo::getDisplayName));
                PreparedStatement executionsForName = connection.prepareStatement("select id, startTime, endTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup, channel, host, teamCityBuildId from testExecution where testId = ? and testProject = ? and startTime >= ? and channel = ? order by startTime desc limit ?");
                PreparedStatement operationsForExecution = connection.prepareStatement("select displayName, tasks, args, gradleOpts, daemon, totalTime, cleanTasks from testOperation where testExecution = ?");
                executionsForName.setString(1, experiment.getScenario());
                executionsForName.setString(2, experiment.getTestProject());
                Timestamp minDate = new Timestamp(LocalDate.now().minusDays(maxDaysOld).toDate().getTime());
                executionsForName.setTimestamp(3, minDate);
                executionsForName.setString(4, channel);
                executionsForName.setInt(5, mostRecentN);
                ResultSet testExecutions = executionsForName.executeQuery();
                while (testExecutions.next()) {
                    long id = testExecutions.getLong(1);
                    CrossBuildPerformanceResults performanceResults = new CrossBuildPerformanceResults();
                    performanceResults.setTestId(experiment.getScenario());
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
                    ResultSet resultSet = operationsForExecution.executeQuery();
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
                    resultSet.close();
                }
                testExecutions.close();
                operationsForExecution.close();
                executionsForName.close();

                return new CrossBuildPerformanceTestHistory(experiment.getScenario(), ImmutableList.copyOf(builds), results);
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load results from datastore '%s'.", db.getUrl()), e);
        }
    }

    protected boolean ignore(CrossBuildPerformanceResults performanceResults) {
        return false;
    }
}
