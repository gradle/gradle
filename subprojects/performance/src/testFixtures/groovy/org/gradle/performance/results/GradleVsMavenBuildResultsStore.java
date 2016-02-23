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
import org.gradle.performance.fixture.BuildDisplayInfo;
import org.gradle.performance.fixture.CrossBuildPerformanceResults;
import org.gradle.performance.fixture.DataReporter;
import org.gradle.performance.fixture.GradleVsMavenBuildPerformanceResults;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.List;

public class GradleVsMavenBuildResultsStore implements ResultsStore, DataReporter<GradleVsMavenBuildPerformanceResults>, Closeable {

    private final File dbFile;
    private final H2FileDb db;

    public GradleVsMavenBuildResultsStore() {
        dbFile = new File(System.getProperty("user.home"), ".gradle-performance-test-data/gvsm-build-results");
        this.db = new H2FileDb(dbFile, new GradleVsMavenBuildResultsSchemaInitializer());
    }


    public void close() throws IOException {
        db.close();
    }


    public List<String> getTestNames() {
        return null;
    }

    public PerformanceTestHistory getTestResults(String testName) {
        return null;
    }

    public PerformanceTestHistory getTestResults(String testName, int mostRecentN) {
        return null;
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
                    /*
                    statement = connection.prepareStatement("insert into testOperation(testExecution, testProject, displayName, tasks, args, gradleOpts, daemon, totalTime, configurationTime, executionTime, heapUsageBytes, totalHeapUsageBytes, maxHeapUsageBytes, maxUncollectedHeapBytes, maxCommittedHeapBytes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    try {
                        for (BuildDisplayInfo displayInfo : results.getBuilds()) {
                            //addOperations(statement, executionId, displayInfo, results.buildResult(displayInfo));
                        }
                    } finally {
                        statement.close();
                    }
                    */
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not open results datastore '%s'.", dbFile), e);
        }
    }

    private static class GradleVsMavenBuildResultsSchemaInitializer implements ConnectionAction<Void> {
        @Override
        public Void execute(Connection connection) throws Exception {
            Statement statement = connection.createStatement();
            statement.execute("create table if not exists testExecution (id bigint identity not null, testId varchar not null, executionTime timestamp not null, versionUnderTest varchar not null, operatingSystem varchar not null, jvm varchar not null, vcsBranch varchar not null, vcsCommit varchar, testGroup varchar not null)");
            statement.close();
            return null;
        }
    }
}
