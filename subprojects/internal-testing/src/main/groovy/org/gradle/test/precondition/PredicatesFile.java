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

package org.gradle.test.precondition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PredicatesFile {
    public static final Set<Set<String>> DEFAULT_ACCEPTED_COMBINATIONS = readAllowedCombinations("/valid-precondition-combinations.csv");
    public static final String PREDICATE_PACKAGE_PREFIX = "org.gradle.test.preconditions.";

    /**
     * Streams a quasi-csv file containing comma-separated values.
     * <p>
     * Note, that for the sake of conciseness, we do not require in the file the whole package name.
     * All class names will be prefixed with {@link #PREDICATE_PACKAGE_PREFIX} when reading them up.
     * <p>
     * The csv is sudo in the sense, that you can comment by using "#"
     *
     * @param resource the resource file being used
     * @return a set of sets of strings representing the values in the file
     */
    public static Set<Set<String>> readAllowedCombinations(String resource) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                Objects.requireNonNull(
                    PredicatesFile.class.getResourceAsStream(resource),
                    String.format("Predicate combination list resource '%s' cannot be found", resource)
                ),
                StandardCharsets.UTF_8
            )
        )) {
            return reader.lines()
                // If a line starts with #, it's a comment
                .filter(line -> !line.startsWith("#"))
                // We are not interested in whitespaces on the line level
                .map(String::trim)
                // If the line was empty, or only contained space (which was cut by the trim before), we skip the line
                .filter(line -> !line.isEmpty())
                // We separate the values
                .map(line -> line.split(","))
                // For each array of entries...
                .map(entries -> Arrays
                    // We stream the entries
                    .stream(entries)
                    // Trim all unnecessary whitespaces off
                    .map(String::trim)
                    // Prefix the package with the implicit name of the predicates
                    .map(name -> PREDICATE_PACKAGE_PREFIX + name)
                    .collect(Collectors.toSet())
                ).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException("Error parsing resource " + resource, e);
        }
    }

    /**
     * Checks if a precondition is present in the allowed precondition list stored
     * in {@code acceptedCombinations}
     */
    public static void checkValidCombinations(List<Class<?>> predicateClasses, Set<Set<String>> acceptedCombinations) {
        if (predicateClasses.isEmpty()) {
            return;
        }
        Set<String> predicateClassNames = predicateClasses.stream()
            .map(Class::getName)
            .collect(Collectors.toSet());
        checkValidNameCombinations(predicateClassNames, acceptedCombinations);
    }

    static void checkValidNameCombinations(Set<String> predicateClassNames, Set<Set<String>> acceptedCombinations) {
        boolean found = acceptedCombinations.contains(predicateClassNames);

        if (!found) {
            String message = String.format(
                "Requested requirements [%s] were not in the list of accepted combinations. " +
                    "Add it to 'subprojects/internal-testing/src/main/resources/valid-precondition-combinations.csv' to be accepted. " +
                    "See the documentation of this class to learn more about this feature.",
                String.join(", ", predicateClassNames)
            );
            throw new IllegalArgumentException(message);
        }
    }
}
