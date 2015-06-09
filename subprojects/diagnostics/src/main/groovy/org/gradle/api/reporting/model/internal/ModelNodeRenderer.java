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
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.logging.StyledTextOutput;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.reporting.ReportRenderer;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.gradle.logging.StyledTextOutput.Style.*;

public class ModelNodeRenderer extends ReportRenderer<ModelNode, TextReportBuilder> {

    private static final int LABEL_LENGTH = 7;

    @Override
    public void render(ModelNode model, TextReportBuilder output) {
        if (model.isHidden()) {
            return;
        }

        StyledTextOutput styledTextoutput = output.getOutput();

        if (model.getPath().equals(ModelPath.ROOT)) {
            styledTextoutput.withStyle(Identifier).format("+ %s", "model");
            styledTextoutput.println();
        } else {
            printNodeName(model, styledTextoutput);
            maybePrintType(model, styledTextoutput);
            printOrigin(model, styledTextoutput);
            maybePrintValue(model, styledTextoutput);
            maybePrintRules(model, styledTextoutput);
        }

        Map<String, ModelNode> links = new TreeMap<String, ModelNode>();
        for (ModelNode node : model.getLinks(ModelType.untyped())) {
            links.put(node.getPath().getName(), node);
        }
        output.collection(links.values(), this);
    }

    public void printNodeName(ModelNode model, StyledTextOutput styledTextoutput) {
        styledTextoutput.withStyle(Identifier).format("+ %s", model.getPath().getName());
        styledTextoutput.println();
    }

    public void printOrigin(ModelNode model, StyledTextOutput styledTextoutput) {
        ModelRuleDescriptor descriptor = model.getDescriptor();
        StringBuffer buffer = new StringBuffer();
        descriptor.describeTo(buffer);
        printNodeAttribute(styledTextoutput, "Creator:", buffer.toString());
    }

    public void maybePrintType(ModelNode model, StyledTextOutput styledTextoutput) {
        Optional<String> typeDescription = model.getTypeDescription();
        if (typeDescription.isPresent()) {
            printNodeAttribute(styledTextoutput, "Type:", typeDescription.get());
        }
    }

    public void maybePrintValue(ModelNode model, StyledTextOutput styledTextoutput) {
        if (model.getLinkCount() == 0) {
            Optional<String> value = model.getValueDescription();
            if (value.isPresent()) {
                printNodeAttribute(styledTextoutput, "Value:", value.get());
            }
        }
    }

    private void maybePrintRules(ModelNode model, StyledTextOutput styledTextoutput) {
        List<ModelRuleDescriptor> executedRules = model.getExecutedRules();
        if (executedRules.size() > 0) {
            printNestedAttributeTitle(styledTextoutput, "Rules: ");
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
        styledTextoutput.withStyle(Identifier).format("      | %s |", title);
        styledTextoutput.println();
    }

    public void printNodeAttribute(StyledTextOutput styledTextoutput, String label, String value) {
        styledTextoutput.withStyle(Identifier).format("      | %s", attributeLabel(label));
        styledTextoutput.withStyle(Description).format(" \t%s |", value);
        //Ideally the closing '|' would be the same style as the opening one but somehow it ends up placed in the middle of the line when we style for a 3rd time.
        styledTextoutput.println();
    }


    private String attributeLabel(String label) {
        return Strings.padEnd(label, LABEL_LENGTH, ' ');
    }
}
