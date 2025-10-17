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

import org.gradle.api.problems.internal.InternalProblem;

import java.io.PrintWriter;

public class StandaloneProblemRenderer {

    private final HeaderRenderer headerRenderer;
    private final BodyRenderer bodyRenderer;
    private final PrintWriter writer;

    StandaloneProblemRenderer(HeaderRenderer headerRenderer, BodyRenderer bodyRenderer, PrintWriter writer) {
        this.headerRenderer = headerRenderer;
        this.bodyRenderer = bodyRenderer;
        this.writer = writer;
    }

    public void render(InternalProblem problem) {
        headerRenderer.render(problem, writer);
        bodyRenderer.render(problem, writer);
    }
}
