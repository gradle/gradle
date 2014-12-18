/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.performance.fixture.*;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.sql.*;
import java.util.*;

public class CrossBuildResultsStore implements ResultsStore, CrossBuildDataReporter {

    private final File dbFile;
    private final H2FileDb db;

    public CrossBuildResultsStore() {
        this(new File(System.getProperty("user.home"), ".gradle-performance-test-data/cross-build-results"));
    }

    public CrossBuildResultsStore(File dbFile) {
        this.dbFile = dbFile;
        this.db = new H2FileDb(dbFile, new CrossBuildResultsSchemaInitializer());
    }

    public void report(final CrossBuildPerformanceResults results) {
        try {
            db.withConnection(new ConnectionAction<Void>() {
                public Void execute(Connection connection) throws Exception {
                    long executionId;
                    PreparedStatement statement = connection.prepareStatement("insert into testExecution(testId, executionTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit) values (?, ?, ?, ?, ?, ?, ?)");
                    try {
                        statement.setString(1, results.getTestId());
                        statement.setTimestamp(2, new Timestamp(results.getTestTime()));
                        statement.setString(3, results.getVersionUnderTest());
                        statement.setString(4, results.getOperatingSystem());
                        statement.setString(5, results.getJvm());
                        statement.setString(6, results.getVcsBranch());
                        statement.setString(7, results.getVcsCommit());
                        statement.execute();
                        ResultSet keys = statement.getGeneratedKeys();
                        keys.next();
                        executionId = keys.getLong(1);
                    } finally {
                        statement.close();
                    }
                    statement = connection.prepareStatement("insert into testOperation(testExecution, testProject, displayName, tasks, args, executionTimeMs, heapUsageBytes, totalHeapUsageBytes, maxHeapUsageBytes, maxUncollectedHeapBytes, maxCommittedHeapBytes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        for (BuildSpecification specification : results.getBuildSpecifications()) {
                            addOperations(statement, executionId, specification, results.buildResult(specification));
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

    private void addOperations(PreparedStatement statement, long executionId, BuildSpecification specification, MeasuredOperationList operations) throws SQLException {
        for (MeasuredOperation operation : operations) {
            statement.setLong(1, executionId);
            statement.setString(2, specification.getProjectName());
            statement.setString(3, specification.getDisplayName());
            statement.setObject(4, specification.getTasksToRun());
            statement.setObject(5, specification.getArgs());
            statement.setBigDecimal(6, operation.getExecutionTime().toUnits(Duration.MILLI_SECONDS).getValue());
            statement.setBigDecimal(7, operation.getTotalMemoryUsed().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(8, operation.getTotalHeapUsage().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(9, operation.getMaxHeapUsage().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(10, operation.getMaxUncollectedHeap().toUnits(DataAmount.BYTES).getValue());
            statement.setBigDecimal(11, operation.getMaxCommittedHeap().toUnits(DataAmount.BYTES).getValue());
            statement.execute();
        }
    }

    public void close() {
        db.close();
    }

    public List<String> getTestNames() {
        try {
            return db.withConnection(new ConnectionAction<List<String>>() {
                public List<String> execute(Connection connection) throws Exception {
                    List<String> testNames = new ArrayList<String>();
                    ResultSet testExecutions = connection.createStatement().executeQuery("select distinct testId from testExecution order by testId");
                    while (testExecutions.next()) {
                        testNames.add(testExecutions.getString(1));
                    }
                    return testNames;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load test history from datastore '%s'.", dbFile), e);
        }
    }

    public CrossBuildTestExecutionHistory getTestResults(final String testName) {
        try {
            return db.withConnection(new ConnectionAction<CrossBuildTestExecutionHistory>() {
                public CrossBuildTestExecutionHistory execute(Connection connection) throws Exception {
                    List<CrossBuildPerformanceResults> results = Lists.newArrayList();
                    Set<BuildSpecification> buildSpecifications = Sets.newTreeSet(new Comparator<BuildSpecification>() {
                        @Override
                        public int compare(BuildSpecification o1, BuildSpecification o2) {
                            return o1.getDisplayName().compareTo(o2.getDisplayName());
                        }
                    });
                    PreparedStatement executionsForName = connection.prepareStatement("select id, executionTime, versionUnderTest, operatingSystem, jvm, vcsBranch, vcsCommit from testExecution where testId = ? order by executionTime desc");
                    PreparedStatement operationsForExecution = connection.prepareStatement("select testProject, displayName, tasks, args, executionTimeMs, heapUsageBytes, totalHeapUsageBytes, maxHeapUsageBytes, maxUncollectedHeapBytes, maxCommittedHeapBytes from testOperation where testExecution = ?");
                    executionsForName.setString(1, testName);
                    ResultSet testExecutions = executionsForName.executeQuery();
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        CrossBuildPerformanceResults performanceResults = new CrossBuildPerformanceResults();
                        performanceResults.setTestId(testName);
                        performanceResults.setTestTime(testExecutions.getTimestamp(2).getTime());
                        performanceResults.setVersionUnderTest(testExecutions.getString(3));
                        performanceResults.setOperatingSystem(testExecutions.getString(4));
                        performanceResults.setJvm(testExecutions.getString(5));
                        performanceResults.setVcsBranch(testExecutions.getString(6).trim());
                        performanceResults.setVcsCommit(testExecutions.getString(7));

                        results.add(performanceResults);

                        operationsForExecution.setLong(1, id);
                        ResultSet builds = operationsForExecution.executeQuery();
                        while (builds.next()) {
                            BuildSpecification specification = new BuildSpecification();
                            specification.setProjectName(builds.getString(1));
                            specification.setDisplayName(builds.getString(2));
                            specification.setTasksToRun(toArray(builds.getObject(3)));
                            specification.setArgs(toArray(builds.getObject(4)));

                            MeasuredOperation operation = new MeasuredOperation();
                            operation.setExecutionTime(Duration.millis(builds.getBigDecimal(5)));
                            operation.setTotalMemoryUsed(DataAmount.bytes(builds.getBigDecimal(6)));
                            operation.setTotalHeapUsage(DataAmount.bytes(builds.getBigDecimal(7)));
                            operation.setMaxHeapUsage(DataAmount.bytes(builds.getBigDecimal(8)));
                            operation.setMaxUncollectedHeap(DataAmount.bytes(builds.getBigDecimal(9)));
                            operation.setMaxCommittedHeap(DataAmount.bytes(builds.getBigDecimal(10)));

                            performanceResults.buildResult(specification).add(operation);
                            buildSpecifications.add(specification);
                        }
                        builds.close();
                    }
                    testExecutions.close();
                    operationsForExecution.close();
                    executionsForName.close();

                    return new CrossBuildTestExecutionHistory(testName, ImmutableList.copyOf(buildSpecifications), results);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load results from datastore '%s'.", dbFile), e);
        }
    }

    private String[] toArray(Object object) {
        Object[] value = (Object[]) object;
        String[] result = new String[value.length];
        for (int i = 0; i < value.length; i++) {
            result[i] = value[i].toString();
        }
        return result;
    }

    private class CrossBuildResultsSchemaInitializer implements ConnectionAction<Void> {
        @Override
        public Void execute(Connection connection) throws Exception {
            Statement statement = connection.createStatement();
            statement.execute("create table if not exists testExecution (id bigint identity not null, testId varchar not null, executionTime timestamp not null, versionUnderTest varchar not null, operatingSystem varchar not null, jvm varchar not null, vcsBranch varchar not null, vcsCommit varchar)");
            statement.execute("create table if not exists testOperation (testExecution bigint not null, testProject varchar not null, displayName varchar not null, tasks array not null, args array not null, executionTimeMs decimal not null, heapUsageBytes decimal not null, totalHeapUsageBytes decimal, maxHeapUsageBytes decimal, maxUncollectedHeapBytes decimal, maxCommittedHeapBytes decimal, foreign key(testExecution) references testExecution(id))");
            statement.close();
            return null;
        }
    }
}
