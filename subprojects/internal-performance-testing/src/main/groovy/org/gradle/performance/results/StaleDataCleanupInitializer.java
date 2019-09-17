/*
 * Copyright 2019 the original author or authors.
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class StaleDataCleanupInitializer implements ConnectionAction<Void> {
    private static final int MAX_DATA_STORE_DAYS = 365;

    @Override
    public Void execute(Connection connection) throws SQLException {
        Timestamp time = Timestamp.valueOf(LocalDateTime.now().minusDays(MAX_DATA_STORE_DAYS));
        runStatement(connection, "delete from testOperation where testExecution in (select id from testExecution where startTime < ?)", time);
        runStatement(connection, "delete from testExecution where startTime < ?", time);
        return null;
    }

    private void runStatement(Connection connection, String sql, Timestamp time) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, time);
            statement.execute();
        }
    }
}
