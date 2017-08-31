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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.gradle.performance.results.ResultsStoreHelper.split;

public class BaseCrossBuildResultsStore<R extends CrossBuildPerformanceResults> implements ResultsStore, DataReporter<R>, Closeable {

    private final PerformanceDatabase db;
    private final String resultType;

    public BaseCrossBuildResultsStore(String resultType) {
        this.db = new PerformanceDatabase("cross-build-results", new CrossBuildResultsSchemaInitializer());
        this.resultType = resultType;
    }

    public void report(final R results) {
        try {
            db.withConnection(new ConnectionAction<Void>() {
                public Void execute(Connection connection) throws SQLException {
                    long executionId;
                    PreparedStatement statement = connection.prepareStatement("insert into testExecution(testId, startTime, endTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup, resultType, channel, host) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        statement.setString(1, results.getTestId());
                        statement.setTimestamp(2, new Timestamp(results.getStartTime()));
                        statement.setTimestamp(3, new Timestamp(results.getEndTime()));
                        statement.setString(4, results.getVersionUnderTest());
                        statement.setString(5, results.getOperatingSystem());
                        statement.setString(6, results.getJvm());
                        statement.setString(7, results.getVcsBranch());
                        statement.setString(8, Joiner.on(",").join(results.getVcsCommits()));
                        statement.setString(9, results.getTestGroup());
                        statement.setString(10, resultType);
                        statement.setString(11, results.getChannel());
                        statement.setString(12, results.getHost());
                        statement.execute();
                        ResultSet keys = statement.getGeneratedKeys();
                        keys.next();
                        executionId = keys.getLong(1);
                    } finally {
                        statement.close();
                    }
                    statement = connection.prepareStatement("insert into testOperation(testExecution, testProject, displayName, tasks, args, gradleOpts, daemon, totalTime, cleanTasks) values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        for (BuildDisplayInfo displayInfo : results.getBuilds()) {
                            addOperations(statement, executionId, displayInfo, results.buildResult(displayInfo));
                        }
                        statement.executeBatch();
                    } finally {
                        statement.close();
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not open results datastore '%s'.", db.getUrl()), e);
        }
    }

    private void addOperations(PreparedStatement statement, long executionId, BuildDisplayInfo displayInfo, MeasuredOperationList operations) throws SQLException {
        for (MeasuredOperation operation : operations) {
            statement.setLong(1, executionId);
            statement.setString(2, displayInfo.getProjectName());
            statement.setString(3, displayInfo.getDisplayName());
            statement.setObject(4, toArray(displayInfo.getTasksToRun()));
            statement.setObject(5, toArray(displayInfo.getArgs()));
            statement.setObject(6, toArray(displayInfo.getGradleOpts()));
            statement.setObject(7, displayInfo.getDaemon());
            statement.setBigDecimal(8, operation.getTotalTime().toUnits(Duration.MILLI_SECONDS).getValue());
            statement.setObject(9, toArray(displayInfo.getCleanTasks()));
            statement.addBatch();
        }
    }

    private String[] toArray(List<String> list) {
        return list == null ? null : list.toArray(new String[0]);
    }

    public void close() {
        db.close();
    }

    public List<String> getTestNames() {
        try {
            return db.withConnection(new ConnectionAction<List<String>>() {
                public List<String> execute(Connection connection) throws SQLException {
                Set<String> testNames = Sets.newLinkedHashSet();
                PreparedStatement testIdsStatement = connection.prepareStatement("select distinct testId, testGroup from testExecution where resultType = ? order by testGroup, testId");
                testIdsStatement.setString(1, resultType);
                ResultSet testExecutions = testIdsStatement.executeQuery();
                while (testExecutions.next()) {
                    testNames.add(testExecutions.getString(1));
                }
                testExecutions.close();
                testIdsStatement.close();
                return Lists.newArrayList(testNames);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load test history from datastore '%s'.", db.getUrl()), e);
        }
    }

    @Override
    public CrossBuildPerformanceTestHistory getTestResults(String testName, String channel) {
        return getTestResults(testName, Integer.MAX_VALUE, Integer.MAX_VALUE, channel);
    }

    public CrossBuildPerformanceTestHistory getTestResults(final String testName, final int mostRecentN, final int maxDaysOld, final String channel) {
        try {
            return db.withConnection(new ConnectionAction<CrossBuildPerformanceTestHistory>() {
                public CrossBuildPerformanceTestHistory execute(Connection connection) throws SQLException {
                    List<CrossBuildPerformanceResults> results = Lists.newArrayList();
                    Set<BuildDisplayInfo> builds = Sets.newTreeSet(new Comparator<BuildDisplayInfo>() {
                        @Override
                        public int compare(BuildDisplayInfo o1, BuildDisplayInfo o2) {
                            return o1.getDisplayName().compareTo(o2.getDisplayName());
                        }
                    });
                    PreparedStatement executionsForName = connection.prepareStatement("select top ? id, startTime, endTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup, channel, host from testExecution where testId = ? and startTime >= ? and channel = ? order by startTime desc");
                    PreparedStatement operationsForExecution = connection.prepareStatement("select testProject, displayName, tasks, args, gradleOpts, daemon, totalTime, cleanTasks from testOperation where testExecution = ?");
                    executionsForName.setInt(1, mostRecentN);
                    executionsForName.setString(2, testName);
                    Timestamp minDate = new Timestamp(LocalDate.now().minusDays(maxDaysOld).toDate().getTime());
                    executionsForName.setTimestamp(3, minDate);
                    executionsForName.setString(4, channel);
                    ResultSet testExecutions = executionsForName.executeQuery();
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        CrossBuildPerformanceResults performanceResults = new CrossBuildPerformanceResults();
                        performanceResults.setTestId(testName);
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

                        if (ignore(performanceResults)) {
                            continue;
                        }

                        results.add(performanceResults);

                        operationsForExecution.setLong(1, id);
                        ResultSet resultSet = operationsForExecution.executeQuery();
                        while (resultSet.next()) {
                            String projectName = resultSet.getString(1);
                            String displayName = resultSet.getString(2);
                            List<String> tasksToRun = toList(resultSet.getObject(3));
                            List<String> cleanTasks = toList(resultSet.getObject(8));
                            List<String> args = toList(resultSet.getObject(4));
                            List<String> gradleOpts = toList(resultSet.getObject(5));
                            Boolean daemon = (Boolean) resultSet.getObject(6);
                            BuildDisplayInfo displayInfo = new BuildDisplayInfo(projectName, displayName, tasksToRun, cleanTasks, args, gradleOpts, daemon);

                            MeasuredOperation operation = new MeasuredOperation();
                            operation.setTotalTime(Duration.millis(resultSet.getBigDecimal(7)));
                            performanceResults.buildResult(displayInfo).add(operation);
                            builds.add(displayInfo);
                        }
                        resultSet.close();
                    }
                    testExecutions.close();
                    operationsForExecution.close();
                    executionsForName.close();

                    return new CrossBuildPerformanceTestHistory(testName, ImmutableList.copyOf(builds), results);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load results from datastore '%s'.", db.getUrl()), e);
        }
    }

    protected boolean ignore(CrossBuildPerformanceResults performanceResults) {
        return false;
    }

    private List<String> toList(Object object) {
        Object[] value = (Object[]) object;
        if (value == null) {
            return null;
        }
        List<String> list = Lists.newLinkedList();
        for (Object aValue : value) {
            list.add(aValue.toString());
        }
        return list;
    }

    private class CrossBuildResultsSchemaInitializer implements ConnectionAction<Void> {
        @Override
        public Void execute(Connection connection) throws SQLException {
            Statement statement = connection.createStatement();
            statement.execute("create table if not exists testExecution (id bigint identity not null, testId varchar not null, startTime timestamp not null, versionUnderTest varchar not null, operatingSystem varchar not null, jvm varchar not null, vcsBranch varchar not null, vcsCommit varchar)");
            statement.execute("create table if not exists testOperation (testExecution bigint not null, testProject varchar not null, displayName varchar not null, tasks array not null, args array not null, totalTime decimal not null, foreign key(testExecution) references testExecution(id))");
            statement.execute("alter table testExecution add column if not exists testGroup varchar");
            statement.execute("update testExecution set testGroup = 'old vs new java plugin' where testGroup is null and testId like '%old vs new java plugin%'");
            statement.execute("update testExecution set testGroup = 'project using variants' where testGroup is null and testId like '%project using variants%'");
            statement.execute("update testExecution set testGroup = testId where testGroup is null");
            statement.execute("alter table testExecution alter column testGroup set not null");
            if (DataBaseSchemaUtil.columnExists(connection, "TESTOPERATION", "EXECUTIONTIMEMS")) {
                statement.execute("alter table testOperation alter column executionTimeMs rename to totalTime");
            }
            statement.execute("alter table testOperation add column if not exists gradleOpts array");
            statement.execute("alter table testOperation add column if not exists daemon boolean");
            statement.execute("alter table testExecution add column if not exists resultType varchar not null default 'cross-build'");
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
            if (!DataBaseSchemaUtil.columnExists(connection, "TESTEXECUTION", "HOST")) {
                statement.execute("alter table testExecution add column if not exists host varchar");
            }
            statement.execute("create index if not exists testExecution_executionTime on testExecution (startTime desc)");
            statement.execute("create index if not exists testExecution_testGroup on testExecution (testGroup)");

            if (!DataBaseSchemaUtil.columnExists(connection, "TESTOPERATION", "CLEANTASKS")) {
                statement.execute("alter table testOperation add column if not exists cleanTasks array");
            }

            DataBaseSchemaUtil.removeOutdatedColumnsFromTestDB(connection, statement);

            statement.close();
            return null;
        }
    }
}
