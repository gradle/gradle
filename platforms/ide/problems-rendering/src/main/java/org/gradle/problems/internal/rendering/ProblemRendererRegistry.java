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

package org.gradle.problems.internal.rendering;

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;

public class ProblemRendererRegistry {

    private static final ProblemRenderer DEFAULT_RENDERER = new DefaultProblemRenderer();
    private static final ProblemRenderer JAVA_COMPILATION_RENDERER = new JavaCompilationRenderer();

    public ProblemRendererRegistry() {
    }

    public ProblemRenderer getRendererFor(ProblemId problemId) {
        boolean isJavaCompilationProblem = problemId.getGroup().equals(GradleCoreProblemGroup.compilation().java()) && !problemId.getName().equals("initialization-failed");
        if (isJavaCompilationProblem) {
            return JAVA_COMPILATION_RENDERER;
        }
        return DEFAULT_RENDERER;
    }
}
