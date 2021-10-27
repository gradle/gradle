/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * A read of the metadata of an external resource.
 *
 * @since 4.0
 */
public final class ExternalResourceReadMetadataBuildOperationType implements BuildOperationType<ExternalResourceReadMetadataBuildOperationType.Details, ExternalResourceReadMetadataBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

        /**
         * The location of the resource.
         * A valid URI.
         */
        String getLocation();

    }

    public interface Result {

    }

    private ExternalResourceReadMetadataBuildOperationType() {
    }

}
