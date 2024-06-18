/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.problems.internal.rendering;

import org.gradle.api.problems.internal.GeneralData;
import org.gradle.api.problems.internal.Problem;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class JavaProblemRenderer implements ProblemRenderer {

    public static final String JAVA_COMPILE_PROBLEM_HEADER = "Java compilation problems";

    private final PrintStream output;

    public JavaProblemRenderer(PrintStream output) {this.output = output;}

    @Override
    public void render(Collection<Problem> problems) {
        this.output.println("* " + JAVA_COMPILE_PROBLEM_HEADER + ":");

        for (Problem problem : problems) {
            Optional.of(problem)
                .map(Problem::getAdditionalData)
                .map(GeneralData.class::cast)
                .map(GeneralData::getAsMap)
                .map(gm -> gm.get("formatted"))
                .ifPresent(this::printEachLine);
        }
    }

    private void printEachLine(String formatted) {
        String[] lines = formatted.split(System.lineSeparator());
        for (String line : lines) {
            this.output.println("    > " + line);
        }
    }
}
