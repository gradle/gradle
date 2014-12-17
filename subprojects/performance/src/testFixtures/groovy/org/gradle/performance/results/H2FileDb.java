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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class H2FileDb {
    private final File dbFile;
    private final ConnectionAction<Void> schemaInitializer;
    private Connection connection;

    public H2FileDb(File dbFile, ConnectionAction<Void> schemaInitializer) {
        this.dbFile = dbFile;
        this.schemaInitializer = schemaInitializer;
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

    public <T> T withConnection(ConnectionAction<T> action) throws Exception {
        if (connection == null) {
            dbFile.getParentFile().mkdirs();
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(String.format("jdbc:h2:%s", dbFile.getAbsolutePath()), "sa", "");
            try {
                schemaInitializer.execute(connection);
            } catch (Exception e) {
                connection.close();
                connection = null;
                throw e;
            }
        }
        return action.execute(connection);
    }
}
