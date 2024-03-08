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

import org.gradle.api.problems.internal.ProblemReport;
import org.gradle.problems.rendering.ProblemRenderer;

import java.util.List;

public class JavaCompilerProblemRenderer implements ProblemRenderer {

    private static final String JAVA_COMPILER_CATEGORY = "org.gradle:compilation:java";

    @Override
    public void render(List<ProblemReport> problems) {
        problems
            .stream()
            .filter(JavaCompilerProblemRenderer::isJavaCompilerProblem)
            .forEach(JavaCompilerProblemRenderer::printFormattedMessageToErrorStream);
    }

    private static void printFormattedMessageToErrorStream(ProblemReport problem) {
        String formatted = (String) problem.getContext().getAdditionalData().get("formatted");
        System.err.println(formatted);
    }

    private static boolean isJavaCompilerProblem(ProblemReport problem) {
        return problem.getDefinition().getCategory().getCategory().startsWith(JAVA_COMPILER_CATEGORY);
    }
}
