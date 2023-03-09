/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.test.fixtures.condition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PredicatesFile {

    /**
     * Streams a sudo-csv file containing comma-separated values.
     * <p>
     * The csv is sudo in the sense, that you can comment by using "#"
     *
     * @param resource the resource file being used
     * @return a list of string arrays representing the values in the file
     */
    public static Stream<String[]> streamCombinationsFile(String resource) {
        Stream<String> lineStream = new BufferedReader(
            new InputStreamReader(
                Objects.requireNonNull(
                    PredicatesFile.class.getResourceAsStream(resource),
                    String.format("Predicate combination list resource '%s' cannot be found", resource)
                ),
                StandardCharsets.UTF_8
            )
        ).lines();

        return lineStream
            .filter(line -> !line.startsWith("#"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .map(line -> line.split(","));
    }

    /**
     * Reads a sudo-csv file containing comma-separated values.
     * <p>
     * The csv is sudo in the sense, that you can comment by using "#"
     *
     * @param resource the resource file being used
     * @return a list of string arrays representing the values in the file
     */
    public static List<String[]> readCombinationsFile(String resource) {
        return streamCombinationsFile(resource).collect(Collectors.toList());
    }

}
