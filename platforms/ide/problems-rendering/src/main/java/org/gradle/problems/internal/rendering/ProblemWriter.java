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
import org.gradle.api.problems.internal.InternalProblem;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders one or more problems to a writer.
 */
public abstract class ProblemWriter {

    private ProblemWriter() {
    }

    /**
     * Renders a single problem to the given writer.
     *
     * @param problem the problem to write
     * @param writer the writer to write to
     */
    public abstract void write(InternalProblem problem, Writer writer);

    /**
     * Renders multiple problems to the given writer.
     *
     * @param problems the problems to write
     * @param writer the writer to write to
     */
    public abstract void write(Collection<InternalProblem> problems, Writer writer);

    /**
     * Creates a simple problem writer that renders each problem individually without grouping.
     * @return the problem writer
     */
    public static ProblemWriter simple() {
        return new SimpleProblemWriter(
            ProblemWriterRegistry.INSTANCE,
            new RenderOptions("Problem found: ", true, true)
        );
    }

    /**
     * Creates a problem writer that writes problems in groups based on their problem id.
     * @return the problem writer
     */
    public static ProblemWriter grouping() {
        return new GroupingProblemWriter(
            ProblemWriterRegistry.INSTANCE,
            new RenderOptions("", false, false));
    }

    /**
     * Writes a single problem on the console.
     */
    private static class SimpleProblemWriter extends ProblemWriter {

        private final ProblemWriterRegistry writerRegistry;
        private final RenderOptions options;

        SimpleProblemWriter(ProblemWriterRegistry writerRegistry, RenderOptions options) {
            this.writerRegistry = writerRegistry;
            this.options = options;
        }

        @Override
        public void write(InternalProblem problem, Writer writer) {
            PrintWriter output = new PrintWriter(writer);
            writerRegistry.problemWriterFor(problem.getDefinition().getId()).write(problem, options, output);
        }

        @Override
        public void write(Collection<InternalProblem> problems, Writer writer) {
            PrintWriter output = new PrintWriter(writer);
            String sep = "";
            for (InternalProblem problem : problems) {
                output.printf(sep);
                sep = "%n";
                writerRegistry.problemWriterFor(problem.getDefinition().getId()).write(problem, options, output);
            }
        }
    }

    /**
     * Writes a collection of problems, grouping them by problem id.
     */
    private static class GroupingProblemWriter extends ProblemWriter {

        private final ProblemWriterRegistry problemWriterRegistry;
        private final RenderOptions options;

        GroupingProblemWriter(ProblemWriterRegistry problemWriterRegistry, RenderOptions options) {
            this.problemWriterRegistry = problemWriterRegistry;
            this.options = options;
        }

        @Override
        public void write(InternalProblem problem, Writer writer) {
            write(Collections.singletonList(problem), writer);
        }

        @Override
        public void write(Collection<InternalProblem> problems, Writer writer) {
            write(problems, new PrintWriter(writer));
        }

        private void write(Collection<InternalProblem> problems, PrintWriter output) {
            // Group problems by problem id
            // When generic rendering is addressed, maybe we also group by the whole problem group hierarchy
            Map<ProblemId, List<InternalProblem>> problemIdListMap = problems.stream().collect(Collectors.groupingBy(internalProblem -> internalProblem.getDefinition().getId()));
            String separator = "";
            for (Map.Entry<ProblemId, List<InternalProblem>> problemIdListEntry : problemIdListMap.entrySet()) {
                renderProblemsById(output, problemIdListEntry.getKey(), problemIdListEntry.getValue(), separator);
                separator = "%n";
            }
        }

        private void renderProblemsById(PrintWriter output, ProblemId problemId, List<InternalProblem> problems, String separator) {
            String sep = separator;
            SelectiveProblemWriter renderer = problemWriterRegistry.problemWriterFor(problemId);
            for (InternalProblem problem : problems) {
                output.printf(sep);
                renderer.write(problem, options, output);
                sep = "%n";
            }
        }
    }

}
