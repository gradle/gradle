/*
 * Copyright 2025 the original author or authors.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * Represents metadata and performance metrics for a SQL execution.
 *
 * @param name The logical name of the operation (e.g., "insertExecution").
 * @param sql The raw SQL statement executed.
 * @param params A list of key parameters used in the query for identification.
 * @param result A string summary of the execution outcome. This unifies various JDBC
 * return types: Booleans (success), Integers (rows affected),
 * int[] (batch update counts), or ResultSets (presence of data).
 * @param durationMs The time taken to execute the operation in milliseconds.
 */
record SQLProfilingData(
    String name,
    String sql,
    List<?> params,
    String result,
    long durationMs
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SQLProfilingData create(String name, String sql, List<?> params, Object rawResult, long startTimeMs) {
        return new SQLProfilingData(name, sql, params, summarize(rawResult), System.currentTimeMillis() - startTimeMs);
    }

    /**
     * Converts various JDBC return types into a human-readable string summary.
     */
    private static String summarize(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof Boolean) {
            return result.toString();
        }
        if (result instanceof Integer) {
            return "rows: " + result;
        }
        if (result instanceof int[] batch) {
            return "batch rows: " + Arrays.stream(batch).sum();
        }
        if (result instanceof java.sql.ResultSet) {
            return "ResultSet returned";
        }
        if (result instanceof String) {
            return (String) result;
        }
        return result.getClass().getSimpleName() + ": " + result.toString();
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
