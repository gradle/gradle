/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.artifact.transforms.spec;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Contains options describing how to run an {@link org.gradle.api.tasks.diagnostics.ArtifactTransformsReportTask}, which describes what features of the data model for
 * a project should be rendered in the output.
 */
public final class ArtifactTransformReportSpec {
    @Nullable
    private final String searchTarget;

    public ArtifactTransformReportSpec(@Nullable String searchTarget) {
        this.searchTarget = searchTarget;
    }

    /**
     * This allow the user to filter which Artifact Transforms are included in the report.
     * <p>
     * Currently, filtering is performed upon the type of the Artifact Transform.  This could be
     * expanded to include name, attributes, or other properties in the future.
     *
     * @return the target Artifact Transforms to report on
     */
    public Optional<String> getSearchTarget() {
        return Optional.ofNullable(searchTarget);
    }
}
