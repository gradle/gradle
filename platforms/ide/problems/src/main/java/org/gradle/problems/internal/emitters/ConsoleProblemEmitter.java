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

package org.gradle.problems.internal.emitters;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.problems.ProblemUtils;
import org.gradle.problems.internal.rendering.JavaCompilationProblems;
import org.gradle.problems.internal.rendering.ProblemWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Writes problems to the console as warnings. Only registered when all warnings are requested
 * ({@link org.gradle.api.logging.configuration.WarningMode#All}).
 */
public class ConsoleProblemEmitter implements ProblemEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleProblemEmitter.class);

    @Override
    public void emit(ProblemInternal problem, @Nullable OperationIdentifier id) {
        if (shouldRender(problem)) {
            render(problem);
        }
    }

    @VisibleForTesting
    static boolean shouldRender(ProblemInternal problem) {
        // Deprecations have their own console output, and the Java compiler already prints its diagnostics.
        return !ProblemUtils.isInGroup(problem, GradleCoreProblemGroup.deprecation())
            && !JavaCompilationProblems.isInJavaCompilationGroup(problem.getDefinition().getId().getGroup());
    }

    private static void render(ProblemInternal problem) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ProblemWriter.simple().write(problem, pw);
        String result = sw.toString();
        LOGGER.warn(result);
    }
}
