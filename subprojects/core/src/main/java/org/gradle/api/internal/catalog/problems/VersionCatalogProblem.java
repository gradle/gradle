/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog.problems;

import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.problems.BaseProblem;
import org.gradle.problems.Solution;
import org.gradle.problems.StandardSeverity;

import java.util.List;
import java.util.function.Supplier;

import static org.apache.commons.lang.StringUtils.capitalize;
import static org.apache.commons.lang.StringUtils.uncapitalize;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderSolutions;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

public class VersionCatalogProblem extends BaseProblem<VersionCatalogProblemId, StandardSeverity, String> {
    VersionCatalogProblem(VersionCatalogProblemId versionCatalogProblemId,
                          StandardSeverity severity,
                          String context,
                          Supplier<String> shortDescription,
                          Supplier<String> longDescription,
                          Supplier<String> reason,
                          Supplier<String> docUrl,
                          List<Supplier<Solution>> solutions) {
        super(versionCatalogProblemId, severity, context, shortDescription, longDescription, reason, docUrl, solutions);
    }

    public void reportInto(TreeFormatter output) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Problem: In " + uncapitalize(getWhere()) + ", " + endLineWithDot(uncapitalize(getShortDescription())));
        getWhy().ifPresent(reason -> {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(reason)));
        });
        renderSolutions(formatter, getPossibleSolutions());
        getDocumentationLink().ifPresent(docLink -> {
            formatter.blankLine();
            formatter.node(docLink);
        });
        output.node(formatter.toString());
    }
}
