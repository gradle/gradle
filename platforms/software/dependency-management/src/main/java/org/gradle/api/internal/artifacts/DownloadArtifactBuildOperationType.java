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

package org.gradle.api.internal.artifacts;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Details about an artifact being downloaded.
 *
 * @since 4.0
 */
public final class DownloadArtifactBuildOperationType implements BuildOperationType<DownloadArtifactBuildOperationType.Details, DownloadArtifactBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

        String getArtifactIdentifier();

    }

    @UsedByScanPlugin
    public interface Result {

        /**
         * The absolute filesystem path of the resolved artifact file.
         *
         * @since 9.6
         */
        String getResolvedFilePath();

        /**
         * The size of the resolved artifact file in bytes.
         *
         * @since 9.6
         */
        long getFileSize();

    }

    public static class DetailsImpl implements Details {

        private final String artifactIdentifier;

        public DetailsImpl(String artifactIdentifier) {
            this.artifactIdentifier = artifactIdentifier;
        }

        @Override
        public String getArtifactIdentifier() {
            return artifactIdentifier;
        }

    }

    public static class ResultImpl implements Result {

        private final String resolvedFilePath;
        private final long fileSize;

        public ResultImpl(String resolvedFilePath, long fileSize) {
            this.resolvedFilePath = resolvedFilePath;
            this.fileSize = fileSize;
        }

        @Override
        public String getResolvedFilePath() {
            return resolvedFilePath;
        }

        @Override
        public long getFileSize() {
            return fileSize;
        }

    }

    private DownloadArtifactBuildOperationType() {
    }

}
