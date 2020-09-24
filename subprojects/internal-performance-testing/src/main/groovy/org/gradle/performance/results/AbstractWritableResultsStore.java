/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.ImmutableMap;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

public abstract class AbstractWritableResultsStore<T extends PerformanceTestResult> implements WritableResultsStore<T> {
    private final PerformanceDatabase db;

    public AbstractWritableResultsStore(PerformanceDatabase db) {
        this.db = db;
    }

    public PerformanceDatabase getDb() {
        return db;
    }

    private static final int LATEST_EXECUTION_TIMES_DAYS = 14;
    private static final String SELECT_LATEST_EXECUTION_TIMES = "with last as\n" +
        "(\n" +
        "   select\n" +
        "   testClass,\n" +
        "   testId,\n" +
        "   testProject,\n" +
        "   operatingSystem,\n" +
        "   max(id) as lastId\n" +
        "   from testExecution\n" +
        "   where startTime > ?\n" +
        "   and operatingSystem like ?\n" +
        "   and (channel in ('commits-master','experiments-master'))\n" +
        "   and testProject is not null\n" +
        "   group by testClass,\n" +
        "   testId,\n" +
        "   testProject\n" +
        ")\n" +
        "select\n" +
        "last.testClass,\n" +
        "last.testId,\n" +
        "last.testProject,\n" +
        "testExecution.startTime,\n" +
        "testExecution.endTime\n" +
        "from last\n" +
        "join testExecution on testExecution.id = last.lastId\n" +
        "order by last.testClass,last.testId,last.testProject";

    @Override
    public Map<PerformanceExperiment, Long> getEstimatedExperimentTimes(OperatingSystem operatingSystem) {
        return db.withConnection("load estimated runtimes", connection -> {
            Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusDays(LATEST_EXECUTION_TIMES_DAYS));
            ImmutableMap.Builder<PerformanceExperiment, Long> builder = ImmutableMap.builder();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_LATEST_EXECUTION_TIMES)) {
                statement.setTimestamp(1, since);
                statement.setString(2, operatingSystem.getSqlMatcher());
                try (ResultSet experimentTimes = statement.executeQuery()) {
                    while (experimentTimes.next()) {
                        String testClass = experimentTimes.getString(1);
                        String testName = experimentTimes.getString(2);
                        String testProject = experimentTimes.getString(3);
                        long startTime = experimentTimes.getTimestamp(4).getTime();
                        long endTime = experimentTimes.getTimestamp(5).getTime();
                        PerformanceExperiment performanceExperiment = new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testName));
                        builder.put(performanceExperiment, endTime - startTime);
                    }
                    return builder.build();
                }
            }
        });
    }

    protected <RESULT> RESULT withConnection(String actionName, ConnectionAction<RESULT> action) {
        return db.withConnection(actionName, action);
    }


        @Override
    public void close() {
        db.close();
    }
}
