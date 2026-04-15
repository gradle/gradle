/*
 * Copyright 2026 the original author or authors.
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

/**
 * An external resource request that was satisfied entirely from the local dependency cache,
 * without contacting the remote repository.
 * <p>
 * Sibling to {@link ExternalResourceReadBuildOperationType}: for a given resource request,
 * exactly one of the two operations is emitted — {@code ExternalResourceRead} when the content
 * was fetched over the wire, {@code ExternalResourceAlreadyPresent} when it was served from
 * the local cache.
 *
 * @since 9.6
 */
public final class ExternalResourceAlreadyPresentBuildOperationType implements BuildOperationType<ExternalResourceAlreadyPresentBuildOperationType.Details, ExternalResourceAlreadyPresentBuildOperationType.Result> {

    public interface Details {

        /**
         * The location of the resource that was served from the local cache.
         * A valid URI.
         */
        String getLocation();

    }

    public interface Result {

        /**
         * The absolute filesystem path of the cached resource file.
         */
        String getResolvedFilePath();

        /**
         * The size of the cached resource file in bytes.
         */
        long getFileSize();

    }

    private ExternalResourceAlreadyPresentBuildOperationType() {
    }

}
