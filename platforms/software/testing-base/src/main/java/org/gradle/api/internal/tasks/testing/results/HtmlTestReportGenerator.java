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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.testing.GenericTestReportGenerator;
import org.gradle.api.internal.tasks.testing.report.generic.MetadataRendererRegistry;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Generates HTML test reports given binary test results.
 */
@NonNullApi
@ServiceScope(Scope.BuildSession.class)
public class HtmlTestReportGenerator {

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final MetadataRendererRegistry metadataRendererRegistry;

    @Inject
    public HtmlTestReportGenerator(
        BuildOperationRunner buildOperationRunner,
        BuildOperationExecutor buildOperationExecutor,
        MetadataRendererRegistry metadataRendererRegistry
    ) {
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
        this.metadataRendererRegistry = metadataRendererRegistry;
    }

    /**
     * Generate an HTML report from the test results in the given results directory.
     * <p>
     * Results are placed in the given report directory.
     *
     * @return The path to the index file of the generated report.
     */
    public Path generateHtmlReport(Path reportsDirectory, Path resultsDirectory) {
        try {
            Files.createDirectories(reportsDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        new GenericTestReportGenerator(Collections.singletonList(resultsDirectory), metadataRendererRegistry).generateReport(buildOperationRunner, buildOperationExecutor, reportsDirectory);
        return reportsDirectory.resolve("index.html");
    }

}
