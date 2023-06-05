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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;

import java.util.Map;

@NonNullApi
public class ProblemsProgressEventConsumer extends ClientForwardingBuildOperationListener implements BuildOperationListener {
    private final BuildOperationIdFactory idFactory;

    public ProblemsProgressEventConsumer(ProgressEventConsumer progressEventConsumer, BuildOperationIdFactory idFactory) {
        super(progressEventConsumer);
        this.idFactory = idFactory;
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        if (details instanceof Problem) {
            Problem problem = (Problem) details;
            Map<String, String> rawAttributes = DefaultProblemEvent.createRawAttributes(problem);
            DefaultProblemDescriptor descriptor = new DefaultProblemDescriptor(new OperationIdentifier(idFactory.nextId()), buildOperationId, rawAttributes);
            DefaultProblemEvent event = new DefaultProblemEvent(rawAttributes, descriptor);
            eventConsumer.progress(event);
        }
    }
}
