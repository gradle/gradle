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

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
                    PreparedStatement statement = connection.prepareStatement("insert into testExecution(executionTime, testName, targetVersion) values (?, ?, ?)");
                    try {
                        statement.setTimestamp(1, new Timestamp(results.getTestTime()));
                        statement.setString(2, results.getDisplayName());
                        statement.setString(3, results.getVersionUnderTest());
                        statement.execute();
                        ResultSet keys = statement.getGeneratedKeys();
                        keys.next();
                        testId = keys.getLong(1);
                    } finally {
                        statement.close();
                    }
                    statement = connection.prepareStatement("insert into results(testExecution, version, executionTimeMs, heapUsageBytes) values (?, ?, ?, ?)");
                    try {
                        addOperations(statement, testId, results.getVersionUnderTest(), results.getCurrent());
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

    public List<PerformanceResults> loadResults() {
        try {
            return withConnection(new ConnectionAction<List<PerformanceResults>>() {
                public List<PerformanceResults> execute(Connection connection) throws Exception {
                    List<PerformanceResults> result = new ArrayList<PerformanceResults>();
                    PreparedStatement preparedStatement = connection.prepareStatement("select version, executionTimeMs, heapUsageBytes from results where testExecution = ?");
                    ResultSet testExecutions = connection.createStatement().executeQuery("select id, executionTime, testName, targetVersion from testExecution order by executionTime, testName");
                    while (testExecutions.next()) {
                        long id = testExecutions.getLong(1);
                        Timestamp executionTime = testExecutions.getTimestamp(2);
                        String testName = testExecutions.getString(3);
                        String versionUnderTest = testExecutions.getString(4);
                        PerformanceResults performanceResults = new PerformanceResults();
                        performanceResults.setTestTime(executionTime.getTime());
                        performanceResults.setDisplayName(testName);
                        performanceResults.setVersionUnderTest(versionUnderTest);
                        result.add(performanceResults);
                        preparedStatement.setLong(1, id);
                        Map<String, BaselineVersion> versions = new TreeMap<String, BaselineVersion>();
                        ResultSet builds = preparedStatement.executeQuery();
                        while (builds.next()) {
                            String version = builds.getString(1);
                            BigDecimal executionTimeMs = builds.getBigDecimal(2);
                            BigDecimal heapUsageBytes = builds.getBigDecimal(3);
                            MeasuredOperation operation = new MeasuredOperation();
                            operation.setExecutionTime(Duration.millis(executionTimeMs));
                            operation.setTotalMemoryUsed(DataAmount.bytes(heapUsageBytes));

                            if (version.equals(versionUnderTest)) {
                                performanceResults.getCurrent().add(operation);
                            } else {
                                BaselineVersion baselineVersion = versions.get(version);
                                if (baselineVersion == null) {
                                    baselineVersion = new BaselineVersion();
                                    baselineVersion.setVersion(version);
                                    versions.put(version, baselineVersion);
                                }
                                baselineVersion.getResults().add(operation);
                            }
                        }
                        performanceResults.setBaselineVersions(new ArrayList<BaselineVersion>(versions.values()));
                    }
                    testExecutions.close();
                    preparedStatement.close();
                    return result;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load results from datastore '%s'.", dbFile), e);
        }
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
            statement.execute("create table if not exists testExecution (id bigint identity not null, executionTime timestamp not null, testName varchar not null, targetVersion varchar not null)");
            statement.execute("create table if not exists results (testExecution bigint not null, version varchar not null, executionTimeMs decimal not null, heapUsageBytes decimal not null, foreign key(testExecution) references testExecution(id))");
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
