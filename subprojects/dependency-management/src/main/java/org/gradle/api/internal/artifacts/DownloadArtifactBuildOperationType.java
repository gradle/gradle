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

    public interface Result {

    }

    public static class DetailsImpl implements Details {

        private String artifactIdentifier;

        public DetailsImpl(String artifactIdentifier) {
            this.artifactIdentifier = artifactIdentifier;
        }

        @Override
        public String getArtifactIdentifier() {
            return artifactIdentifier;
        }

    }

    public final static Result RESULT = new Result() {
    };

    private DownloadArtifactBuildOperationType() {
    }

}
