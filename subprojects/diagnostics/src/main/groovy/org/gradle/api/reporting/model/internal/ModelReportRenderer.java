/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.model.internal;

import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.type.ModelType;
import org.gradle.reporting.ReportRenderer;

import java.util.Map;
import java.util.TreeMap;

public class ModelReportRenderer extends TextReportRenderer {
    private final NodeRenderer nodeRenderer = new NodeRenderer();

    public void render(ModelNode node) {
        nodeRenderer.render(node, getBuilder());
    }

    private static class NodeRenderer extends ReportRenderer<ModelNode, TextReportBuilder> {
        @Override
        public void render(ModelNode model, TextReportBuilder output) {
            if (model.isHidden()) {
                return;
            }

            if (model.getPath().equals(ModelPath.ROOT)) {
                output.getOutput().println("model");
            } else {
                output.getOutput().println(model.getPath().getName());
            }

            Map<String, ModelNode> links = new TreeMap<String, ModelNode>();
            for (ModelNode node : model.getLinks(ModelType.untyped())) {
                links.put(node.getPath().getName(), node);
            }
            output.collection(links.values(), this);
        }
    }
}
