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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.locations.ProblemLocation;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemDetails;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;

import java.io.IOException;

@NonNullApi
public class ProblemsProgressEventConsumer extends ClientForwardingBuildOperationListener implements BuildOperationListener {
    private final BuildOperationIdFactory idFactory;
    private final Gson gson;

    public ProblemsProgressEventConsumer(ProgressEventConsumer progressEventConsumer, BuildOperationIdFactory idFactory) {
        super(progressEventConsumer);
        this.idFactory = idFactory;
        this.gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(ProblemLocation.class, new ProblemLocationSerializer())
            .create();
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        if (details instanceof DefaultProblemProgressDetails) {
            Problem problem = ((DefaultProblemProgressDetails) details).getProblem();
            eventConsumer.progress(
                new DefaultProblemEvent(
                    new DefaultProblemDescriptor(
                        new OperationIdentifier(
                            idFactory.nextId()
                        ),
                        buildOperationId),
                    new DefaultProblemDetails(
                        gson.toJson(problem)
                    )
                )
            );
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        super.finished(buildOperation, result);
    }

    @NonNullApi
    private static final class ProblemLocationSerializer extends TypeAdapter<ProblemLocation> {

        // This GSON instance doesn't have the ProblemLocationSerializer registered
        // Otherwise, we would create an infinite loop at the inner call to `toJson`
        private final Gson gson = new Gson();

        @Override
        public void write(JsonWriter out, ProblemLocation value) throws IOException {
            out.beginObject();
            out.name("type");
            out.value(value.getClass().getName());
            out.name("data");
            out.jsonValue(gson.toJson(value));
            out.endObject();
        }

        @Override
        public ProblemLocation read(JsonReader in) {
            // Event consumer does not need to deserialize problem locations
            throw new UnsupportedOperationException();
        }

    }
}
