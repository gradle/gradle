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

package org.gradle.api.tasks.diagnostics.internal.artifact.transforms.renderer;

import com.google.common.collect.Streams;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.tasks.diagnostics.internal.artifact.transforms.model.ArtifactTransformReportModel;
import org.gradle.api.tasks.diagnostics.internal.artifact.transforms.model.ReportArtifactTransform;
import org.gradle.api.tasks.diagnostics.internal.artifact.transforms.spec.ArtifactTransformReportSpec;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.List;

/**
 * A type of {@link AbstractArtifactTransformReportRenderer} that can be used to render a {@link ArtifactTransformReportModel}
 * to the console with richly formatted output.
 */
public final class ConsoleArtifactTransformReportRenderer extends AbstractArtifactTransformReportRenderer<StyledTextOutput> {
    private final DocumentationRegistry documentationRegistry;
    private StyledTextOutput output;
    private int depth;

    public ConsoleArtifactTransformReportRenderer(ArtifactTransformReportSpec spec, DocumentationRegistry documentationRegistry) {
        super(spec);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void render(ArtifactTransformReportModel model, StyledTextOutput output) {
        this.depth = 0;
        this.output = output;

        boolean searchingByType = spec.getSearchTarget().isPresent();
        ArtifactTransformReportModel modelToReport = searchingByType ? spec.getSearchTarget().map(model::filterTransformsByType).orElse(model) : model;

        final boolean hasAnyTransforms = !modelToReport.getTransforms().isEmpty();
        if (hasAnyTransforms) {
            writeResults(modelToReport);
        } else {
            writeCompleteAbsenceOfResults(modelToReport, searchingByType);
        }
    }

    private void writeResults(ArtifactTransformReportModel data) {
        writeArtifactTransforms(data.getTransforms());
        writeSuggestions(data.getTransforms());
    }

    private void writeArtifactTransforms(List<ReportArtifactTransform> artifactTransforms) {
        artifactTransforms.forEach(this::writeArtifactTransform);
    }

    private void writeArtifactTransform(ReportArtifactTransform artifactTransform) {
        writeArtifactTransformNameHeader(artifactTransform);
        writeType(artifactTransform);
        writeAttributes(artifactTransform);
        newLine();
    }

    private void writeArtifactTransformNameHeader(ReportArtifactTransform artifactTransform) {
        printHeader(() -> {
            output.style(StyledTextOutput.Style.Identifier).println(artifactTransform.getDisplayName());
        });
    }

    private void writeType(ReportArtifactTransform artifactTransform) {
        output.style(StyledTextOutput.Style.Description).text("Type: ");
        output.style(StyledTextOutput.Style.Normal).println(artifactTransform.getTransformClass().getName());

        output.style(StyledTextOutput.Style.Description).text("Cacheable: ");
        output.style(StyledTextOutput.Style.Normal).println(artifactTransform.isCacheable() ? "Yes" : "No");
    }

    @SuppressWarnings("CodeBlock2Expr")
    private void writeAttributes(ReportArtifactTransform artifactTransform) {
        Integer maxNameLength = Streams.concat(artifactTransform.getFromAttributes().keySet().stream(), artifactTransform.getToAttributes().keySet().stream())
            .map(a -> a.getName().length())
            .max(Integer::compare)
            .orElse(0);

        printSection("From Attributes:", () -> artifactTransform.getFromAttributes().asMap().forEach((key, value) -> {
            writeAttribute(maxNameLength, key.getName(), value);
        }));
        printSection("To Attributes:", () -> artifactTransform.getToAttributes().asMap().forEach((key, value) -> {
            writeAttribute(maxNameLength, key.getName(), value);
        }));
    }

    private void writeAttribute(Integer max, String name, Object value) {
        indent(true);
        valuePair(StringUtils.rightPad(name, max), value.toString());
        newLine();
    }

    private void writeSuggestions(List<ReportArtifactTransform> transforms) {
        output.style(StyledTextOutput.Style.Info);
        if (transforms.stream().anyMatch(t -> !t.isCacheable())) {
            output.println("Some artifact transforms are not cacheable.  This can have negative performance impacts.  See more documentation here: " + documentationRegistry.getDocumentationFor("artifact_transforms", "artifact_transforms_with_caching") + ".");
        }
    }

    private void writeCompleteAbsenceOfResults(ArtifactTransformReportModel model, boolean searchingByType) {
        message("There are no transforms registered in " + model.getProjectDisplayName() + (searchingByType ? " with this type." : "."));
    }

    private void printHeader(Runnable action) {
        output.style(StyledTextOutput.Style.Header);
        indent(false);
        output.println("--------------------------------------------------");
        indent(false);
        action.run();
        output.style(StyledTextOutput.Style.Header);
        indent(false);
        output.println("--------------------------------------------------");
        output.style(StyledTextOutput.Style.Normal);
    }

    private void printSection(String title, Runnable action) {
        indent(false);
        output.style(StyledTextOutput.Style.Description).text(title);
        output.style(StyledTextOutput.Style.Normal);
        try {
            depth++;
            newLine();
            action.run();
        } finally {
            depth--;
        }
    }

    private void valuePair(String key, String value) {
        output.style(StyledTextOutput.Style.Identifier).text(key);
        output.style(StyledTextOutput.Style.Normal).text(" = " + value);
    }

    private void indent(boolean bullet) {
        output.text(StringUtils.repeat("    ", depth));
        if (depth > 0 && bullet) {
            output.withStyle(StyledTextOutput.Style.Normal).text("- ");
        }
    }

    private void message(String msg) {
        output.text(msg).println();
    }

    private void newLine() {
        output.println();
    }
}
