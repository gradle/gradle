/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.reporting.model.ModelReport;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelReferenceNode;
import org.gradle.model.internal.type.ModelType;
import org.gradle.reporting.ReportRenderer;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.*;

public class ModelNodeRenderer extends ReportRenderer<ModelNode, TextReportBuilder> {

    private static final int LABEL_LENGTH = 7;

    private final boolean showHidden;
    private final ModelReport.Format format;

    public ModelNodeRenderer(boolean showHidden, ModelReport.Format format) {
        this.showHidden = showHidden;
        this.format = format;
    }

    private boolean omitDetails() {
        return ModelReport.Format.SHORT == format;
    }

    @Override
    public void render(ModelNode model, TextReportBuilder output) {
        if (model.isHidden() && !showHidden) {
            return;
        }

        StyledTextOutput styledTextoutput = output.getOutput();

        if (!model.getPath().equals(ModelPath.ROOT)) {
            printNodeName(model, styledTextoutput);
            maybePrintType(model, styledTextoutput);
            maybePrintValue(model, styledTextoutput);
            printCreator(model, styledTextoutput);
            maybePrintRules(model, styledTextoutput);
        }

        if (model instanceof ModelReferenceNode) {
            return;
        }

        Map<String, ModelNode> links = new TreeMap<>();
        for (ModelNode node : model.getLinks(ModelType.untyped())) {
            links.put(node.getPath().getName(), node);
        }
        output.collection(links.values(), this);
    }

    public void printNodeName(ModelNode model, StyledTextOutput styledTextoutput) {
        Optional<String> value = getNodeValue(model);
        boolean hasValue = value != null && value.isPresent();
        String intro = omitDetails() && model.getLinkCount() == 0 && hasValue ? "|" : "+";
        styledTextoutput.withStyle(Identifier).format("%s %s", intro, model.getPath().getName());
        if (omitDetails() && hasValue) {
            styledTextoutput.withStyle(Description).format(" = %s", value.get());
        }
        styledTextoutput.println();
    }

    private Optional<String> getNodeValue(ModelNode model) {
        if (model.getLinkCount() == 0 || model instanceof ModelReferenceNode) {
            return model.getValueDescription();
        }
        return null;
    }

    public void printCreator(ModelNode model, StyledTextOutput styledTextoutput) {
        if (omitDetails()) {
            return;
        }
        ModelRuleDescriptor descriptor = model.getDescriptor();
        StringBuffer buffer = new StringBuffer();
        descriptor.describeTo(buffer);
        printNodeAttribute(styledTextoutput, "Creator:", buffer.toString());
    }

    public void maybePrintType(ModelNode model, StyledTextOutput styledTextoutput) {
        if (omitDetails()) {
            return;
        }
        Optional<String> typeDescription = model.getTypeDescription();
        if (typeDescription.isPresent()) {
            printNodeAttribute(styledTextoutput, "Type:", typeDescription.get());
        }
    }

    public void maybePrintValue(ModelNode model, StyledTextOutput styledTextoutput) {
        if (omitDetails()) {
            return;
        }
        Optional<String> value = getNodeValue(model);
        if (value != null && value.isPresent()) {
            printNodeAttribute(styledTextoutput, "Value:", value.get());
        }

    }

    private void maybePrintRules(ModelNode model, StyledTextOutput styledTextoutput) {
        if (omitDetails()) {
            return;
        }
        Iterable<ModelRuleDescriptor> executedRules = uniqueExecutedRulesExcludingCreator(model);
        if (!Iterables.isEmpty(executedRules)) {
            printNestedAttributeTitle(styledTextoutput, "Rules:");
            for (ModelRuleDescriptor ruleDescriptor : executedRules) {
                printNestedAttribute(styledTextoutput, "⤷ " + ruleDescriptor.toString());
            }
        }
    }

    private void printNestedAttribute(StyledTextOutput styledTextoutput, String value) {
        styledTextoutput.withStyle(Normal).format("         %s", value);
        styledTextoutput.println();
    }

    private void printNestedAttributeTitle(StyledTextOutput styledTextoutput, String title) {
        styledTextoutput.withStyle(Identifier).format("      | %s", title);
        styledTextoutput.println();
    }

    public void printNodeAttribute(StyledTextOutput styledTextoutput, String label, String value) {
        styledTextoutput.withStyle(Identifier).format("      | %s", attributeLabel(label));
        styledTextoutput.withStyle(Description).format(" \t%s", value);
        styledTextoutput.println();
    }


    private String attributeLabel(String label) {
        return Strings.padEnd(label, LABEL_LENGTH, ' ');
    }

    static Iterable<ModelRuleDescriptor> uniqueExecutedRulesExcludingCreator(final ModelNode model) {
        Iterable<ModelRuleDescriptor> filtered = model.getExecutedRules().stream().filter(input -> !input.equals(model.getDescriptor())).collect(Collectors.toList());
        return ImmutableSet.copyOf(filtered);
    }
}
