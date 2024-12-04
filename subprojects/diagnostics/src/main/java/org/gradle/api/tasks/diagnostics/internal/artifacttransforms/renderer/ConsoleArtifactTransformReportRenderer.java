/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.artifacttransforms.renderer;

import com.google.common.collect.Streams;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.diagnostics.internal.artifacttransforms.model.ArtifactTransformReportModel;
import org.gradle.api.tasks.diagnostics.internal.artifacttransforms.model.ReportArtifactTransform;
import org.gradle.api.tasks.diagnostics.internal.artifacttransforms.spec.ArtifactTransformReportSpec;
import org.gradle.internal.logging.text.StyledTextOutput;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The {@link AbstractArtifactTransformReportRenderer} extension that can be used to render a {@link ArtifactTransformReportModel}
 * to the console with richly formatted output.
 */
public final class ConsoleArtifactTransformReportRenderer extends AbstractArtifactTransformReportRenderer<StyledTextOutput> {
    private StyledTextOutput output;
    private int depth;

    public ConsoleArtifactTransformReportRenderer(ArtifactTransformReportSpec spec) {
        super(spec);
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
        writeLegend(data.getTransforms());
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

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void writeArtifactTransformNameHeader(ReportArtifactTransform artifactTransform) {
        printHeader(() -> {
            output.style(StyledTextOutput.Style.Normal).text("Transform ");
            if (artifactTransform.isNamed()) {
                output.style(StyledTextOutput.Style.Identifier).println(artifactTransform.getName().get());
            } else {
                output.style(StyledTextOutput.Style.Description).println("(u)");
            }
        });
    }

    private void writeType(ReportArtifactTransform artifactTransform) {
        output.style(StyledTextOutput.Style.Description).text("Type: ");
        output.style(StyledTextOutput.Style.Normal).text(artifactTransform.getTransformClass().getName());

        if (artifactTransform.isCacheable()) {
            output.style(StyledTextOutput.Style.Description).println(" (c)");
        } else {
            newLine();
        }
    }

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

    private void writeLegend(List<ReportArtifactTransform> configs) {
        boolean hasUnnamed =  !configs.stream().allMatch(ReportArtifactTransform::isNamed);
        boolean hasCacheable = configs.stream().anyMatch(ReportArtifactTransform::isCacheable);

        output.style(StyledTextOutput.Style.Info);
        if (hasUnnamed) {
            output.println("(u) Unnamed artifact transform. Using setName() when registering transforms will make them easier to identify.");
        }
        if (hasCacheable) {
            output.println("(c) Artifact transform has a type marked with @CacheableTransform. Asserts the transformation is idempotent and results can be reused without rerunning the transform.");
        }
    }

    private void writeCompleteAbsenceOfResults(ArtifactTransformReportModel model, boolean searchingByType) {
        message("There are no transforms registered in project '" + model.getProjectName() + "'" + (searchingByType ? " with this type." : "."));
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
        printSection(title, null, action);
    }

    private void printSection(String title, @Nullable String description, Runnable action) {
        indent(false);
        output.style(StyledTextOutput.Style.Description).text(title);
        output.style(StyledTextOutput.Style.Normal);
        if (description != null) {
            output.text(" : " + description);
        }
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
