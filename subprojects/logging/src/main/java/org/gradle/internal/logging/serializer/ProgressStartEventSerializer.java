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

/**
 * Since Gradle creates a high volume of progress events, this serializer trades simplicity
 * for the smallest possible serialized form. It uses a single byte indicating the presence of optional
 * fields instead of writing an "absent" byte for each of them like most of our serializers do.
 * It also encodes the {@link BuildOperationCategory} in this byte, since that enum only has 3 values
 * for the forseeable future.
 */
public class ProgressStartEventSerializer implements Serializer<ProgressStartEvent> {
    private static final byte PARENT_PROGRESS_ID = 1;
    private static final byte SHORT_DESCRIPTION = 2;
    private static final byte LOGGING_HEADER = 4;
    private static final byte BUILD_OPERATION_ID = 8;
    private static final byte PARENT_BUILD_OPERATION_ID = 16;
    private static final byte BUILD_OPERATION_CATEGORY_TASK = 32;
    private static final byte BUILD_OPERATION_CATEGORY_PROJECT = 64;

    @Override
    public void write(Encoder encoder, ProgressStartEvent event) throws Exception {
        byte flags = 0;
        OperationIdentifier parentProgressOperationId = event.getParentProgressOperationId();
        if (parentProgressOperationId != null) {
            flags |= PARENT_PROGRESS_ID;
        }
        String shortDescription = event.getShortDescription();
        if (shortDescription != null) {
            flags |= SHORT_DESCRIPTION;
        }
        String loggingHeader = event.getLoggingHeader();
        if (loggingHeader != null) {
            flags |= LOGGING_HEADER;
        }
        Object buildOperationId = event.getBuildOperationId();
        if (buildOperationId != null) {
            flags |= BUILD_OPERATION_ID;
        }
        Object parentBuildOperationId = event.getParentBuildOperationId();
        if (parentBuildOperationId != null) {
            flags |= PARENT_BUILD_OPERATION_ID;
        }
        BuildOperationCategory buildOperationCategory = event.getBuildOperationCategory();
        if (buildOperationCategory == BuildOperationCategory.CONFIGURE_PROJECT) {
            flags |= BUILD_OPERATION_CATEGORY_PROJECT;
        } else if (buildOperationCategory == BuildOperationCategory.TASK) {
            flags |= BUILD_OPERATION_CATEGORY_TASK;
        } else if (buildOperationCategory != BuildOperationCategory.UNCATEGORIZED) {
            throw new IllegalArgumentException("Can't handle build operation category " + buildOperationCategory);
        }

        encoder.writeByte(flags);

        encoder.writeSmallLong(event.getProgressOperationId().getId());
        if (parentProgressOperationId != null) {
            encoder.writeSmallLong(parentProgressOperationId.getId());
        }
        encoder.writeLong(event.getTimestamp());
        encoder.writeString(event.getCategory());
        encoder.writeString(event.getDescription());
        if (shortDescription != null) {
            encoder.writeString(shortDescription);
        }
        if (loggingHeader != null) {
            encoder.writeString(loggingHeader);
        }
        encoder.writeString(event.getStatus());
        encoder.writeInt(event.getTotalProgress());
        if (buildOperationId != null) {
            encoder.writeSmallLong(((OperationIdentifier) buildOperationId).getId());
        }
        if (parentBuildOperationId != null) {
            encoder.writeSmallLong(((OperationIdentifier) parentBuildOperationId).getId());
        }
    }

    @Override
    public ProgressStartEvent read(Decoder decoder) throws Exception {
        byte flags = decoder.readByte();
        OperationIdentifier progressOperationId = new OperationIdentifier(decoder.readSmallLong());

        OperationIdentifier parentProgressOperationId = null;
        if ((flags & PARENT_PROGRESS_ID) != 0) {
            parentProgressOperationId = new OperationIdentifier(decoder.readSmallLong());
        }

        long timestamp = decoder.readLong();
        String category = decoder.readString();
        String description = decoder.readString();

        String shortDescription = null;
        if ((flags & SHORT_DESCRIPTION) != 0) {
            shortDescription = decoder.readString();
        }

        String loggingHeader = null;
        if ((flags & LOGGING_HEADER) != 0) {
            loggingHeader = decoder.readString();
        }

        String status = decoder.readString();
        int totalProgress = decoder.readInt();

        Object buildOperationId = null;
        if ((flags & BUILD_OPERATION_ID) != 0) {
            buildOperationId = new OperationIdentifier(decoder.readSmallLong());
        }

        Object parentBuildOperationId = null;
        if ((flags & PARENT_BUILD_OPERATION_ID) != 0) {
            parentBuildOperationId = new OperationIdentifier(decoder.readSmallLong());

        }

        BuildOperationCategory buildOperationCategory;
        if ((flags & BUILD_OPERATION_CATEGORY_PROJECT) == BUILD_OPERATION_CATEGORY_PROJECT) {
            buildOperationCategory = BuildOperationCategory.CONFIGURE_PROJECT;
        } else if ((flags & BUILD_OPERATION_CATEGORY_TASK) == BUILD_OPERATION_CATEGORY_TASK) {
            buildOperationCategory = BuildOperationCategory.TASK;
        } else {
            buildOperationCategory = BuildOperationCategory.UNCATEGORIZED;
        }

        return new ProgressStartEvent(progressOperationId, parentProgressOperationId, timestamp, category, description, shortDescription, loggingHeader, status, totalProgress, buildOperationId, parentBuildOperationId, buildOperationCategory);
    }
}
