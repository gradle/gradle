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

import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;
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
    private static final short PARENT_PROGRESS_ID = 1;
    private static final short SHORT_DESCRIPTION = 1 << 2;
    private static final short SHORT_DESCRIPTION_IS_DESCRIPTION = 1 << 3;
    private static final short LOGGING_HEADER = 1 << 4;
    private static final short LOGGING_HEADER_IS_SHORT_DESCRIPTION = 1 << 5;
    private static final short STATUS = 1 << 6;
    private static final short BUILD_OPERATION_ID = 1 << 7;
    private static final short BUILD_OPERATION_ID_IS_PROGRESS_ID = 1 << 8;
    private static final short BUILD_OPERATION_START = 1 << 9;
    private static final short PARENT_BUILD_OPERATION_ID = 1 << 10;
    private static final short PARENT_BUILD_OPERATION_ID_IS_PARENT_PROGRESS_ID = 1 << 11;
    private static final short CATEGORY_OFFSET = 12;
    private static final short CATEGORY_MASK = 0x7;

    @Override
    public void write(Encoder encoder, ProgressStartEvent event) throws Exception {
        int flags = 0;
        OperationIdentifier parentProgressOperationId = event.getParentProgressOperationId();
        if (parentProgressOperationId != null) {
            flags |= PARENT_PROGRESS_ID;
        }
        String shortDescription = event.getShortDescription();
        if (shortDescription != null) {
            if (shortDescription.equals(event.getDescription())) {
                // Optimize for a common case
                // Should instead have a null short description in this case
                flags |= SHORT_DESCRIPTION_IS_DESCRIPTION;
            } else {
                flags |= SHORT_DESCRIPTION;
            }
        }
        String loggingHeader = event.getLoggingHeader();
        if (loggingHeader != null) {
            if (loggingHeader.equals(shortDescription)) {
                // Optimize for a common case
                // Should instead get rid of the logging header
                flags |= LOGGING_HEADER_IS_SHORT_DESCRIPTION;
            } else {
                flags |= LOGGING_HEADER;
            }
        }
        if (!event.getStatus().isEmpty()) {
            flags |= STATUS;
        }
        OperationIdentifier buildOperationId = event.getBuildOperationId();
        if (buildOperationId != null) {
            if (buildOperationId.equals(event.getProgressOperationId())) {
                flags |= BUILD_OPERATION_ID_IS_PROGRESS_ID;
            } else {
                flags |= BUILD_OPERATION_ID;
            }
        }
        OperationIdentifier parentBuildOperationId = event.getParentBuildOperationId();
        if (parentBuildOperationId != null) {
            if (parentBuildOperationId.equals(event.getParentProgressOperationId())) {
                flags |= PARENT_BUILD_OPERATION_ID_IS_PARENT_PROGRESS_ID;
            } else {
                flags |= PARENT_BUILD_OPERATION_ID;
            }
        }
        BuildOperationCategory buildOperationCategory = event.getBuildOperationCategory();
        flags |= (buildOperationCategory.ordinal() & CATEGORY_MASK) << CATEGORY_OFFSET;
        if (event.isBuildOperationStart()) {
            flags |= BUILD_OPERATION_START;
        }

        encoder.writeSmallInt(flags);

        encoder.writeSmallLong(event.getProgressOperationId().getId());
        if (parentProgressOperationId != null) {
            encoder.writeSmallLong(parentProgressOperationId.getId());
        }
        encoder.writeLong(event.getTimestamp());
        encoder.writeString(event.getCategory());
        encoder.writeString(event.getDescription());
        if ((flags & SHORT_DESCRIPTION) != 0) {
            encoder.writeString(shortDescription);
        }
        if ((flags & LOGGING_HEADER) != 0) {
            encoder.writeString(loggingHeader);
        }
        if ((flags & STATUS) != 0) {
            encoder.writeString(event.getStatus());
        }
        encoder.writeInt(event.getTotalProgress());

        if ((flags & BUILD_OPERATION_ID) != 0) {
            encoder.writeSmallLong(buildOperationId.getId());
        }
        if ((flags & PARENT_BUILD_OPERATION_ID) != 0) {
            encoder.writeSmallLong(parentBuildOperationId.getId());
        }
    }

    @Override
    public ProgressStartEvent read(Decoder decoder) throws Exception {
        long flags = decoder.readSmallInt();
        OperationIdentifier progressOperationId = new OperationIdentifier(decoder.readSmallLong());

        OperationIdentifier parentProgressOperationId = null;
        if ((flags & PARENT_PROGRESS_ID) != 0) {
            parentProgressOperationId = new OperationIdentifier(decoder.readSmallLong());
        }

        long timestamp = decoder.readLong();
        String category = decoder.readString();
        String description = decoder.readString();

        String shortDescription = null;
        if ((flags & SHORT_DESCRIPTION_IS_DESCRIPTION) != 0) {
            shortDescription = description;
        } else if ((flags & SHORT_DESCRIPTION) != 0) {
            shortDescription = decoder.readString();
        }

        String loggingHeader = null;
        if ((flags & LOGGING_HEADER_IS_SHORT_DESCRIPTION) != 0) {
            loggingHeader = shortDescription;
        } else if ((flags & LOGGING_HEADER) != 0) {
            loggingHeader = decoder.readString();
        }

        String status = "";
        if ((flags & STATUS) != 0) {
            status = decoder.readString();
        }
        int totalProgress = decoder.readInt();

        boolean buildOperationStart = (flags & BUILD_OPERATION_START) != 0;

        OperationIdentifier buildOperationId = null;
        if ((flags & BUILD_OPERATION_ID) != 0) {
            buildOperationId = new OperationIdentifier(decoder.readSmallLong());
        } else if ((flags & BUILD_OPERATION_ID_IS_PROGRESS_ID) != 0) {
            buildOperationId = progressOperationId;
        }

        OperationIdentifier parentBuildOperationId = null;
        if ((flags & PARENT_BUILD_OPERATION_ID) != 0) {
            parentBuildOperationId = new OperationIdentifier(decoder.readSmallLong());
        } else if ((flags & PARENT_BUILD_OPERATION_ID_IS_PARENT_PROGRESS_ID) != 0) {
            parentBuildOperationId = parentProgressOperationId;
        }

        BuildOperationCategory buildOperationCategory = BuildOperationCategory.values()[(int)((flags >> CATEGORY_OFFSET) & CATEGORY_MASK)];

        return new ProgressStartEvent(
            progressOperationId,
            parentProgressOperationId,
            timestamp,
            category,
            description,
            shortDescription,
            loggingHeader,
            status,
            totalProgress,
            buildOperationStart,
            buildOperationId,
            parentBuildOperationId,
            buildOperationCategory
        );
    }
}
