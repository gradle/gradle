/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.serializer;

import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.progress.BuildOperationCategory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

public class ProgressStartEventSerializer implements Serializer<ProgressStartEvent> {
    private final Serializer<BuildOperationCategory> buildOperationCategorySerializer;
    private static final long EMPTY_BUILD_OPERATION_ID = 0;
    private static final long EMPTY_PROGRESS_OPERATION_ID = 0;

    public ProgressStartEventSerializer(Serializer<BuildOperationCategory> buildOperationCategorySerializer) {
        this.buildOperationCategorySerializer = buildOperationCategorySerializer;
    }

    @Override
    public void write(Encoder encoder, ProgressStartEvent event) throws Exception {
        encoder.writeSmallLong(event.getProgressOperationId().getId());
        if (event.getParentProgressOperationId() == null) {
            encoder.writeSmallLong(EMPTY_PROGRESS_OPERATION_ID);
        } else {
            encoder.writeSmallLong(event.getParentProgressOperationId().getId());
        }
        encoder.writeLong(event.getTimestamp());
        encoder.writeString(event.getCategory());
        encoder.writeString(event.getDescription());
        encoder.writeNullableString(event.getShortDescription());
        encoder.writeNullableString(event.getLoggingHeader());
        encoder.writeString(event.getStatus());
        if (event.getBuildOperationId() == null) {
            encoder.writeSmallLong(EMPTY_BUILD_OPERATION_ID);
        } else {
            encoder.writeSmallLong(((OperationIdentifier) event.getBuildOperationId()).getId());
        }
        if (event.getParentBuildOperationId() == null) {
            encoder.writeSmallLong(EMPTY_BUILD_OPERATION_ID);
        } else {
            encoder.writeSmallLong(((OperationIdentifier) event.getParentBuildOperationId()).getId());
        }
        buildOperationCategorySerializer.write(encoder, event.getBuildOperationCategory());
    }

    @Override
    public ProgressStartEvent read(Decoder decoder) throws Exception {
        OperationIdentifier progressOperationId = new OperationIdentifier(decoder.readSmallLong());

        long parentProgressOpIdValue = decoder.readSmallLong();
        OperationIdentifier parentProgressOperationId = parentProgressOpIdValue == EMPTY_PROGRESS_OPERATION_ID ? null : new OperationIdentifier(parentProgressOpIdValue);

        long timestamp = decoder.readLong();
        String category = decoder.readString();
        String description = decoder.readString();
        String shortDescription = decoder.readNullableString();
        String loggingHeader = decoder.readNullableString();
        String status = decoder.readString();

        long buildOpIdValue = decoder.readSmallLong();
        Object buildOperationId = buildOpIdValue == EMPTY_BUILD_OPERATION_ID ? null : new OperationIdentifier(buildOpIdValue);

        long parentBuildOpIdValue = decoder.readSmallLong();
        Object parentBuildOperationId = parentBuildOpIdValue == EMPTY_BUILD_OPERATION_ID ? null : new OperationIdentifier(parentBuildOpIdValue);

        BuildOperationCategory buildOperationCategory = buildOperationCategorySerializer.read(decoder);
        return new ProgressStartEvent(progressOperationId, parentProgressOperationId, timestamp, category, description, shortDescription, loggingHeader, status, buildOperationId, parentBuildOperationId, buildOperationCategory);
    }
}
