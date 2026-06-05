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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.LinkedHashSet;
import java.util.Set;

class IncompatibleDependencyAttributesMessageBuilder {
    static String buildMergeErrorMessage(ModuleResolveState module, AttributeMergingException e) {
        Attribute<?> attribute = e.getAttribute();
        TreeFormatter fmt = new TreeFormatter();

        fmt.node("Cannot select a variant of '" + module.getId());
        fmt.append("' because the dependency requirements request incompatible values for attribute '");
        fmt.append(attribute.getName());
        fmt.append("'.");

        Set<EdgeState> incomingEdges = new LinkedHashSet<>();
        module.visitAllIncomingEdges(incomingEdges::add);

        Set<String> distinctValues = new LinkedHashSet<>();
        for (EdgeState incomingEdge : incomingEdges) {
            ComponentSelector componentSelector = incomingEdge.getSelector().getSelector();
            Object value = null;
            if (componentSelector instanceof ModuleComponentSelector) {
                value = ((ModuleComponentSelector) componentSelector).getAttributes().getAttribute(attribute);
            }
            distinctValues.add(value == null ? "<no value>" : value.toString());
        }

        fmt.startChildren();
        if (!distinctValues.isEmpty()) {
            fmt.node("Requested values: " + String.join(", ", distinctValues));
        }

        for (EdgeState incomingEdge : incomingEdges) {
            SelectorState selector = incomingEdge.getSelector();
            for (String path : MessageBuilderHelper.formattedPathsTo(incomingEdge)) {
                String requestedAttribute = formatAttributeQuery(selector, attribute);
                fmt.node(path + " " + requestedAttribute);
            }
        }
        fmt.endChildren();

        fmt.node("Possible solutions:");
        fmt.startChildren();
        fmt.node("- Configure all dependencies to use the same '" + attribute.getName() + "' attribute value.");
        fmt.node("- For advanced cases where different values should be treated as compatible, define a compatibility rule.");
        fmt.node("See: " + new DocumentationRegistry().getDocumentationFor("variant_attributes") + " for details on attributes and compatibility rules.");
        fmt.endChildren();

        return fmt.toString();
    }

    private static String formatAttributeQuery(SelectorState state, Attribute<?> attribute) {
        ComponentSelector selector = state.getSelector();
        if (selector instanceof ModuleComponentSelector) {
            StringBuilder sb = new StringBuilder("wants '" + state.getRequested() + "' with attribute " + attribute.getName() + " = ");
            Object value = selector.getAttributes().getAttribute(attribute);
            sb.append(value == null ? "<no value>" : value.toString());
            return sb.toString();
        } else {
            // This is a safety net, it's unsure whether this can happen, because it's likely (certain?)
            // that for a specific module resolve state, all selectors are of the same type
            return "doesn't provide any value for attribute " + attribute.getName();
        }
    }
}
