/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.graph;

import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.internal.logging.text.StyledTextOutput;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class SimpleNodeRenderer implements NodeRenderer {
    @Override
    public void renderNode(StyledTextOutput output, RenderableDependency node, boolean alreadyRendered) {
        output.text(node.getName());
        switch (node.getResolutionState()) {
            case FAILED:
                output.withStyle(Failure).text(" FAILED");
                break;
            case RESOLVED:
                if (alreadyRendered && !node.getChildren().isEmpty()) {
                    output.withStyle(Info).text(" (*)");
                }
                break;
            case UNRESOLVED:
                output.withStyle(Info).text(" (n)");
                break;
        }
    }
}
