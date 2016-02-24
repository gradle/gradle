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

package org.gradle.performance.results;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.performance.fixture.*;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.gradle.performance.results.ResultsStoreHelper.*;

public class GradleVsMavenBuildResultsStore implements ResultsStore, DataReporter<GradleVsMavenBuildPerformanceResults>, Closeable {

    private final File dbFile;
    private final H2FileDb db;

    public GradleVsMavenBuildResultsStore(File dbFile) {
        this.dbFile = dbFile;
        this.db = new H2FileDb(dbFile, new GradleVsMavenBuildResultsSchemaInitializer());
    }

    public GradleVsMavenBuildResultsStore() {
        this(new File(System.getProperty("user.home"), ".gradle-performance-test-data/gvsm-build-results"));
    }


    public void close() throws IOException {
        db.close();
    }


    public List<String> getTestNames() {
        try {
            return db.withConnection(new ConnectionAction<List<String>>() {
                public List<String> execute(Connection connection) throws Exception {
                    List<String> testNames = new ArrayList<String>();
                    ResultSet testGroups = connection.createStatement().executeQuery("select distinct testGroup from testExecution order by testGroup");
                    PreparedStatement testIdsStatement = connection.prepareStatement("select distinct testId from testExecution where testGroup = ? order by testId");
                    while (testGroups.next()) {
                        testIdsStatement.setString(1, testGroups.getString(1));
                        ResultSet testExecutions = testIdsStatement.executeQuery();
                        while (testExecutions.next()) {
                            testNames.add(testExecutions.getString(1));
                        }
                        testExecutions.close();
                    }
                    testIdsStatement.close();
                    testGroups.close();
                    return testNames;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load test history from datastore '%s'.", dbFile), e);
        }
    }

    @Override
    public GradleVsMavenBuildPerformanceTestHistory getTestResults(String testName) {
        return getTestResults(testName, Integer.MAX_VALUE);
    }

    public GradleVsMavenBuildPerformanceTestHistory getTestResults(final String testName, final int mostRecentN) {
        try {
            return db.withConnection(new ConnectionAction<GradleVsMavenBuildPerformanceTestHistory>() {
                public GradleVsMavenBuildPerformanceTestHistory execute(Connection connection) throws Exception {
                    List<GradleVsMavenBuildPerformanceResults> results = Lists.newArrayList();
                    Set<BuildDisplayInfo> builds = Sets.newTreeSet(new Comparator<BuildDisplayInfo>() {
                        @Override
                        public int compare(BuildDisplayInfo o1, BuildDisplayInfo o2) {
                            return o1.getDisplayName().compareTo(o2.getDisplayName());
                        }
                    });
                    PreparedStatement executionsForName = connection.prepareStatement("select top ? id, executionTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup from testExecution where testId = ? order by executionTime desc");
                    PreparedStatement operationsForExecution = connection.prepareStatement("select testProject, displayName, tasks, args, gradleOpts, daemon, totalTime, configurationTime, executionTime, heapUsageBytes, totalHeapUsageBytes, maxHeapUsageBytes, maxUncollectedHeapBytes, maxCommittedHeapBytes from testOperation where testExecution = ?");
                    executionsForName.setInt(1, mostRecentN);
                    executionsForName.setString(2, testName);
                    ResultSet testExecutions = executionsForName.executeQuery();
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        GradleVsMavenBuildPerformanceResults performanceResults = new GradleVsMavenBuildPerformanceResults();
                        performanceResults.setTestId(testName);
                        performanceResults.setTestTime(testExecutions.getTimestamp(2).getTime());
                        performanceResults.setVersionUnderTest(testExecutions.getString(3));
                        performanceResults.setOperatingSystem(testExecutions.getString(4));
                        performanceResults.setJvm(testExecutions.getString(5));
                        performanceResults.setVcsBranch(testExecutions.getString(6).trim());
                        performanceResults.setVcsCommits(splitVcsCommits(testExecutions.getString(7)));
                        performanceResults.setTestGroup(testExecutions.getString(8));

                        if (ignore(performanceResults)) {
                            continue;
                        }

                        results.add(performanceResults);

                        operationsForExecution.setLong(1, id);
                        ResultSet resultSet = operationsForExecution.executeQuery();
                        while (resultSet.next()) {
                            BuildDisplayInfo displayInfo = new BuildDisplayInfo(
                                resultSet.getString(1),
                                resultSet.getString(2),
                                toList(resultSet.getObject(3)),
                                toList(resultSet.getObject(4)),
                                toList(resultSet.getObject(5)),
                                (Boolean)resultSet.getObject(6)
                            );

                            MeasuredOperation operation = new MeasuredOperation();
                            operation.setTotalTime(Duration.millis(resultSet.getBigDecimal(7)));
                            operation.setConfigurationTime(Duration.millis(resultSet.getBigDecimal(8)));
                            operation.setExecutionTime(Duration.millis(resultSet.getBigDecimal(9)));
                            operation.setTotalMemoryUsed(DataAmount.bytes(resultSet.getBigDecimal(10)));
                            operation.setTotalHeapUsage(DataAmount.bytes(resultSet.getBigDecimal(11)));
                            operation.setMaxHeapUsage(DataAmount.bytes(resultSet.getBigDecimal(12)));
                            operation.setMaxUncollectedHeap(DataAmount.bytes(resultSet.getBigDecimal(13)));
                            operation.setMaxCommittedHeap(DataAmount.bytes(resultSet.getBigDecimal(14)));

                            performanceResults.buildResult(displayInfo).add(operation);
                            builds.add(displayInfo);
                        }
                        resultSet.close();
                    }
                    testExecutions.close();
                    operationsForExecution.close();
                    executionsForName.close();

                    return new GradleVsMavenBuildPerformanceTestHistory(testName, ImmutableList.copyOf(builds), results);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load results from datastore '%s'.", dbFile), e);
        }
    }

    protected boolean ignore(GradleVsMavenBuildPerformanceResults performanceResults) {
        return false;
    }

    public void report(final GradleVsMavenBuildPerformanceResults results) {
        try {
            db.withConnection(new ConnectionAction<Void>() {
                public Void execute(Connection connection) throws Exception {
                    long executionId;
                    PreparedStatement statement = connection.prepareStatement("insert into testExecution(testId, executionTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit, testGroup) values (?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        statement.setString(1, results.getTestId());
                        statement.setTimestamp(2, new Timestamp(results.getTestTime()));
                        statement.setString(3, results.getVersionUnderTest());
                        statement.setString(4, results.getOperatingSystem());
                        statement.setString(5, results.getJvm());
                        statement.setString(6, results.getVcsBranch());
                        statement.setString(7, Joiner.on(",").join(results.getVcsCommits()));
                        statement.setString(8, results.getTestGroup());
                        statement.execute();
                        ResultSet keys = statement.getGeneratedKeys();
                        keys.next();
                        executionId = keys.getLong(1);
                    } finally {
                        statement.close();
                    }
                    statement = connection.prepareStatement("insert into testOperation(testExecution, testProject, displayName, tasks, args, gradleOpts, daemon, totalTime, configurationTime, executionTime, heapUsageBytes, totalHeapUsageBytes, maxHeapUsageBytes, maxUncollectedHeapBytes, maxCommittedHeapBytes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        for (BuildDisplayInfo displayInfo : results.getBuilds()) {
                            addOperations(statement, executionId, displayInfo, results.buildResult(displayInfo));
                        }
                    } finally {
                        statement.close();
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not open results datastore '%s'.", dbFile), e);
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
            statement.setBigDecimal(9, operation.getConfigurationTime().toUnits(Duration.MILLI_SECONDS).getValue());
            statement.setBigDecimal(10, operation.getExecutionTime().toUnits(Duration.MILLI_SECONDS).getValue());
            statement.setBigDecimal(11, operation.getTotalMemoryUsed().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(12, operation.getTotalHeapUsage().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(13, operation.getMaxHeapUsage().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(14, operation.getMaxUncollectedHeap().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(15, operation.getMaxCommittedHeap().toUnits(DataAmount.BYTES).getValue());
            statement.execute();
        }
    }

    private static class GradleVsMavenBuildResultsSchemaInitializer implements ConnectionAction<Void> {
        @Override
        public Void execute(Connection connection) throws Exception {
            Statement statement = connection.createStatement();
            statement.execute("create table if not exists testExecution (id bigint identity not null, testId varchar not null, executionTime timestamp not null, versionUnderTest varchar not null, operatingSystem varchar not null, jvm varchar not null, vcsBranch varchar not null, vcsCommit varchar, testGroup varchar not null)");
            statement.execute("create table if not exists testOperation (testExecution bigint not null, testProject varchar not null, displayName varchar not null, tasks array not null, args array not null, gradleOpts array, daemon boolean, totalTime decimal not null, configurationTime decimal not null, executionTime decimal not null, heapUsageBytes decimal not null, totalHeapUsageBytes decimal not null, maxHeapUsageBytes decimal not null, maxUncollectedHeapBytes decimal not null, maxCommittedHeapBytes decimal not null, foreign key(testExecution) references testExecution(id))");
            statement.close();
            return null;
        }
    }
}
