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

import java.io.PrintWriter;
import java.io.Writer;

/**
 * Base class for rendering problems to a text output.
 */
public class ProblemRendererFactory {

    private ProblemRendererFactory() {
    }

    public static GroupingProblemRenderer groupingProblemRenderer(Writer output) {
        PrintWriter writer = new PrintWriter(output);
        return new GroupingProblemRenderer(
            new HeaderRenderer(
                new HeaderRenderOptions("", false),
                writer
            ),
            new BodyRenderer(writer),
            writer);
    }

    public static StandaloneProblemRenderer standaloneProblemRenderer(Writer output) {
        PrintWriter writer = new PrintWriter(output);
        return new StandaloneProblemRenderer(
            new HeaderRenderer(
                new HeaderRenderOptions("Problem found: ", true),
                writer
            ),
            new BodyRenderer(writer)
        );
    }
}
