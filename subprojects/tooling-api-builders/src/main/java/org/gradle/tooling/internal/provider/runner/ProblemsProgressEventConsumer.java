/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemLocation;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;

import javax.annotation.Nonnull;

import static com.google.common.base.Strings.nullToEmpty;

@NonNullApi
public class ProblemsProgressEventConsumer extends ClientForwardingBuildOperationListener implements BuildOperationListener {
    private final BuildOperationIdFactory idFactory;
    private final BiMap<Throwable, OperationIdentifier> seenProblems = HashBiMap.create(10);

    public ProblemsProgressEventConsumer(ProgressEventConsumer progressEventConsumer, BuildOperationIdFactory idFactory) {
        super(progressEventConsumer);
        this.idFactory = idFactory;
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        if (details instanceof Problem) {
            Problem problem = (Problem) details;
            Throwable problemCause = problem.getCause();
            if(seenProblems.containsKey(problemCause)) {
                return;
            }
            seenProblems.put(problemCause, buildOperationId);

            DefaultProblemDescriptor descriptor = new DefaultProblemDescriptor(new OperationIdentifier(idFactory.nextId()), buildOperationId);
            ProblemLocation where = problem.getWhere();
            DefaultProblemEvent event = new DefaultProblemEvent(
                descriptor,
                problem.getProblemGroup().toString(),
                problem.getMessage(),
                problem.getSeverity().toString(),
                where == null ? null : where.getPath(),
                where == null ? null : where.getLine(),
                where == null ? null : where.getColumn(),
                getDocumentationFor(problem),
                problem.getDescription(),
                problem.getSolutions(),
                problemCause, problem.getProblemType(), problem.getAdditionalMetadata());
            eventConsumer.progress(event);
        }
    }

    @Nonnull
    private static String getDocumentationFor(Problem problem) {
        DocLink docLink = problem.getDocumentationLink();
        if(docLink == null) {
            return "";
        }
        return nullToEmpty(docLink.documentationUrl());
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        //TODO: this might be solved using DefaultBuildOperationAncestryTracker
//        seenProblems.inverse().remove(buildOperation.getId());
        super.finished(buildOperation, result);
    }

}
