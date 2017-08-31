/*
 * Copyright 2017 the original author or authors.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class DataBaseSchemaUtil {
    private static final String[] REMOVED_COLUMNS = new String[] {
        "heapUsageBytes",
        "totalHeapUsageBytes",
        "maxHeapUsageBytes",
        "maxUncollectedHeapBytes",
        "maxCommittedHeapBytes",
        "compileTotalTime",
        "gcTotalTime",
        "executionTime",
        "configurationTime"
    };

    static void removeOutdatedColumnsFromTestDB(Connection connection, Statement statement) throws SQLException {
        for (String removedColumn : REMOVED_COLUMNS) {
            if (columnExists(connection, "TESTOPERATION", removedColumn.toUpperCase())) {
                statement.execute("alter table testOperation drop column " + removedColumn);
            }
            if (columnExists(connection, "TESTEXECUTION", removedColumn.toUpperCase())) {
                statement.execute("alter table testExecution drop column " + removedColumn);
            }
        }
    }

    static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        ResultSet columns = null;
        boolean exists;

        try {
            columns = connection.getMetaData().getColumns(null, null, table, column);
            exists = columns.next();
        } finally {
            if (columns != null) {
                columns.close();
            }
        }
        return exists;
    }
}
