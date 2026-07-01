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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.logging.text.TreeFormatter;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Thrown when two or more incoming dependency requirements for the same module request
 * conflicting values for the same attribute.
 * <p>
 * This typically occurs when multiple dependency constraints — declared directly or
 * contributed transitively through other projects — disagree on the value of a single
 * attribute for a shared target module.
 * <p>
 * Implements {@link ResolutionProvider} so suggested fixes render in the build
 * output's "Try" section rather than embedded in the exception message.
 */
public class IncompatibleDependencyAttributesException extends GraphValidationException implements ResolutionProvider {

    private final List<String> resolutions;

    public IncompatibleDependencyAttributesException(ModuleResolveState module, AttributeMergingException cause) {
        super(buildMessage(module, cause.getAttribute()));
        this.resolutions = buildResolutions(cause.getAttribute());
    }

    private static String buildMessage(ModuleResolveState module, Attribute<?> attribute) {
        TreeFormatter fmt = new TreeFormatter();

        fmt.node("Cannot select a variant of '" + module.getId());
        fmt.append("' because the dependency requirements request incompatible values for attribute '");
        fmt.append(attribute.getName());
        fmt.append("'.");

        Set<EdgeState> incomingEdges = new LinkedHashSet<>();
        module.visitAllIncomingEdges(incomingEdges::add);

        Set<String> distinctValues = new LinkedHashSet<>();
        for (EdgeState edge : incomingEdges) {
            Object value = findRequestedAttributeValue(edge, attribute);
            if (value != null) {
                distinctValues.add("'" + value + "'");
            }
        }

        fmt.startChildren();
        if (!distinctValues.isEmpty()) {
            fmt.node("Requested values: " + String.join(", ", distinctValues));
        }

        for (EdgeState edge : incomingEdges) {
            Object value = findRequestedAttributeValue(edge, attribute);
            if (value == null) {
                continue;
            }
            SelectorState selector = edge.getSelector();
            boolean isConstraint = edge.getDependencyMetadata().isConstraint();
            for (String path : MessageBuilderHelper.formattedPathsTo(edge)) {
                fmt.node(path + " " + formatAttributeQuery(selector, attribute, value, isConstraint));
            }
        }
        fmt.endChildren();

        return fmt.toString();
    }

    private static @Nullable Object findRequestedAttributeValue(EdgeState edge, Attribute<?> attribute) {
        ComponentSelector selector = edge.getSelector().getSelector();
        if (selector instanceof ModuleComponentSelector) {
            return selector.getAttributes().getAttribute(attribute);
        }
        return null;
    }

    private static String formatAttributeQuery(SelectorState state, Attribute<?> attribute, Object value, boolean isConstraint) {
        String verb = isConstraint ? "requires" : "depends on";
        return verb + " '" + state.getRequested() + "' with attribute '" + attribute.getName() + "' = '" + value + "'";
    }

    private static List<String> buildResolutions(Attribute<?> attribute) {
        DocumentationRegistry docs = new DocumentationRegistry();
        return ImmutableList.of(
            "Configure all dependencies to use the same value for the attribute '" + attribute.getName() + "'.",
            "For advanced cases where different values should be treated as compatible, define a compatibility rule. See: " + docs.getDocumentationFor("variant_attributes", "sec:abm-compatibility-rules") + "."
        );
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
