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

import org.gradle.performance.fixture.*;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * A {@link DataReporter} implementation that stores results in an H2 relational database.
 */
public class ResultsStore implements DataReporter {
    private final File dbFile;
    private Connection connection;

    public ResultsStore(File dbFile) {
        this.dbFile = dbFile;
    }

    public void report(final PerformanceResults results) {
        try {
            withConnection(new ConnectionAction<Void>() {
                public Void execute(Connection connection) throws Exception {
                    long testId;
                    PreparedStatement statement = connection.prepareStatement("insert into testExecution(testId, executionTime, targetVersion, testProject, tasks, args, operatingSystem, jvm) values (?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        statement.setString(1, results.getTestId());
                        statement.setTimestamp(2, new Timestamp(results.getTestTime()));
                        statement.setString(3, results.getVersionUnderTest());
                        statement.setString(4, results.getTestProject());
                        statement.setObject(5, results.getTasks());
                        statement.setObject(6, results.getArgs());
                        statement.setString(7, results.getOperatingSystem());
                        statement.setString(8, results.getJvm());
                        statement.execute();
                        ResultSet keys = statement.getGeneratedKeys();
                        keys.next();
                        testId = keys.getLong(1);
                    } finally {
                        statement.close();
                    }
                    statement = connection.prepareStatement("insert into testOperation(testExecution, version, executionTimeMs, heapUsageBytes) values (?, ?, ?, ?)");
                    try {
                        addOperations(statement, testId, null, results.getCurrent());
                        for (BaselineVersion baselineVersion : results.getBaselineVersions()) {
                            addOperations(statement, testId, baselineVersion.getVersion(), baselineVersion.getResults());
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

    private void addOperations(PreparedStatement statement, long testId, String version, MeasuredOperationList operations) throws SQLException {
        for (MeasuredOperation operation : operations) {
            statement.setLong(1, testId);
            statement.setString(2, version);
            statement.setBigDecimal(3, operation.getExecutionTime().toUnits(Duration.MILLI_SECONDS).getValue());
            statement.setBigDecimal(4, operation.getTotalMemoryUsed().toUnits(DataAmount.BYTES).getValue());
            statement.execute();
        }
    }

    public List<String> getTestNames() {
        try {
            return withConnection(new ConnectionAction<List<String>>() {
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

    public TestExecutionHistory getTestResults(final String testName) {
        try {
            return withConnection(new ConnectionAction<TestExecutionHistory>() {
                public TestExecutionHistory execute(Connection connection) throws Exception {
                    List<PerformanceResults> results = new ArrayList<PerformanceResults>();
                    Set<String> allVersions = new TreeSet<String>();
                    PreparedStatement executionsForName = connection.prepareStatement("select id, executionTime, targetVersion, testProject, tasks, args, operatingSystem, jvm from testExecution where testId = ? order by executionTime desc");
                    PreparedStatement buildsForTest = connection.prepareStatement("select version, executionTimeMs, heapUsageBytes from testOperation where testExecution = ?");
                    executionsForName.setString(1, testName);
                    ResultSet testExecutions = executionsForName.executeQuery();
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        PerformanceResults performanceResults = new PerformanceResults();
                        performanceResults.setTestId(testName);
                        performanceResults.setTestTime(testExecutions.getTimestamp(2).getTime());
                        performanceResults.setVersionUnderTest(testExecutions.getString(3));
                        performanceResults.setTestProject(testExecutions.getString(4));
                        performanceResults.setTasks(toArray(testExecutions.getObject(5)));
                        performanceResults.setArgs(toArray(testExecutions.getObject(6)));
                        performanceResults.setOperatingSystem(testExecutions.getString(7));
                        performanceResults.setJvm(testExecutions.getString(8));

                        results.add(performanceResults);

                        buildsForTest.setLong(1, id);
                        ResultSet builds = buildsForTest.executeQuery();
                        while (builds.next()) {
                            String version = builds.getString(1);
                            BigDecimal executionTimeMs = builds.getBigDecimal(2);
                            BigDecimal heapUsageBytes = builds.getBigDecimal(3);
                            MeasuredOperation operation = new MeasuredOperation();
                            operation.setExecutionTime(Duration.millis(executionTimeMs));
                            operation.setTotalMemoryUsed(DataAmount.bytes(heapUsageBytes));

                            if (version == null) {
                                performanceResults.getCurrent().add(operation);
                            } else {
                                BaselineVersion baselineVersion = performanceResults.baseline(version);
                                baselineVersion.getResults().add(operation);
                                allVersions.add(version);
                            }
                        }
                    }
                    testExecutions.close();
                    buildsForTest.close();
                    executionsForName.close();

                    return new TestExecutionHistory(new ArrayList<String>(allVersions), results);
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

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Could not close datastore '%s'.", dbFile), e);
            } finally {
                connection = null;
            }
        }
    }

    private <T> T withConnection(ConnectionAction<T> action) throws Exception {
        if (connection == null) {
            dbFile.getParentFile().mkdirs();
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(String.format("jdbc:h2:%s", dbFile.getAbsolutePath()), "sa", "");
        }
        try {
            Statement statement = connection.createStatement();
            statement.execute("create table if not exists testExecution (id bigint identity not null, testId varchar not null, executionTime timestamp not null, targetVersion varchar not null, testProject varchar not null, tasks array not null, args array not null, operatingSystem varchar not null, jvm varchar not null)");
            statement.execute("create table if not exists testOperation (testExecution bigint not null, version varchar, executionTimeMs decimal not null, heapUsageBytes decimal not null, foreign key(testExecution) references testExecution(id))");
            statement.close();
        } catch (Exception e) {
            connection.close();
            connection = null;
        }
        return action.execute(connection);
    }

    private interface ConnectionAction<T> {
        T execute(Connection connection) throws Exception;
    }
}
