/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Set;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.MessageBuilderHelper.pathTo;

class IncompatibleDependencyAttributesMessageBuilder {
    static String buildMergeErrorMessage(ModuleResolveState module, AttributeMergingException e) {
        Attribute<?> attribute = e.getAttribute();
        TreeFormatter fmt = new TreeFormatter();
        fmt.node("Cannot select a variant of '" + module.getId());
        fmt.append("' because different values for attribute '");
        fmt.append(attribute.toString());
        fmt.append("' are requested");
        fmt.startChildren();
        Set<EdgeState> incomingEdges = module.getIncomingEdges();
        incomingEdges.addAll(module.getUnattachedEdges());
        for (EdgeState incomingEdge : incomingEdges) {
            SelectorState selector = incomingEdge.getSelector();
            for (String path : pathTo(incomingEdge, false)) {
                String requestedAttribute = formatAttributeQuery(selector, attribute);
                fmt.node(path + " " + requestedAttribute);
            }
        }
        fmt.endChildren();
        return fmt.toString();
    }

    private static String formatAttributeQuery(SelectorState state, Attribute<?> attribute) {
        ComponentSelector selector = state.getComponentSelector();
        if (selector instanceof ModuleComponentSelector) {
            return "wants '" + state.getComponentSelector() + "' with attribute " + attribute.getName() + " = " + selector.getAttributes().getAttribute(attribute);
        } else {
            // This is a safety net, it's unsure whether this can happen, because it's likely (certain?)
            // that for a specific module resolve state, all selectors are of the same type
            return "doesn't provide any value for attribute " + attribute.getName();
        }
    }

}
