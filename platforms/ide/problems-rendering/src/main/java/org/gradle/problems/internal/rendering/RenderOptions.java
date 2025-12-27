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

/**
 * Customization options for rendering problems.
 */
class RenderOptions {

    private final String prefix;
    private final boolean renderId;
    private final boolean renderSolutions;

    public RenderOptions(
        String prefix,
        boolean renderId,
        boolean renderSolutions
    ) {
        this.prefix = prefix;
        this.renderId = renderId;
        this.renderSolutions = renderSolutions;
    }

    /**
     * The prefix to use for the first line the rendered problem.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Whether to write the problem identifier at the end of the first line.
     */
    public boolean isRenderId() {
        return renderId;
    }

    /**
     * Whether to render the solutions with the problem.
     */
    public boolean isRenderSolutions() {
        return renderSolutions;
    }
}
