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

package org.gradle.problems.internal;

import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.problems.internal.Problem;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.cc.impl.problems.ConfigurationCacheReport;
import org.gradle.internal.cc.impl.problems.ProblemReportDetails;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.configuration.problems.ProblemFactory;
import org.gradle.internal.configuration.problems.StructuredMessage;
import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultProblemsReportCreator implements ProblemReportCreator {

    private final ConfigurationCacheReport report;
    private final ProblemFactory problemFactory;

    public DefaultProblemsReportCreator(
        ExecutorFactory executorFactory,
        TemporaryFileProvider temporaryFileProvider,
        InternalOptions internalOptions,
        ProblemFactory problemFactory
    ){
        this.problemFactory = problemFactory;
        report = new ConfigurationCacheReport(executorFactory, temporaryFileProvider, internalOptions, "problem-report");
    }

    @Override
    public String getId() {
        return "DefaultProblemsReportCreator";
    }

    @Override
    public void report(File reportDir, ProblemConsumer validationFailures) {
        StructuredMessage.Builder builder = new StructuredMessage.Builder();
        builder.text("text");
        report.writeReportFileTo(reportDir, new ProblemReportDetails("buildDisplayName", "cacheAction", builder.build(), "requested tasks", 1));
    }

    @Override
    public void emit(Problem problem, @Nullable OperationIdentifier id) {
        StructuredMessage.Builder builder = new StructuredMessage.Builder();
        String displayName = problem.getDefinition().getId().getDisplayName();
        builder.text(displayName == null ? "<no display name>" : displayName);

        report.onProblem(problemFactory.problem(builder.build(), problem.getException(), null, false));
    }
}
