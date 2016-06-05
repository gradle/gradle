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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.api.Action;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.model.eclipse.EclipseProjectDependency;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.util.GradleVersion;

import java.io.Serializable;

public class EclipseModelCompatibilityMapping implements Action<SourceObjectMapping>, Serializable {

    private final boolean versionSupportsEclipseProjectIdentifier;

    public EclipseModelCompatibilityMapping(VersionDetails versionDetails) {
        GradleVersion targetGradleVersion = GradleVersion.version(versionDetails.getVersion());
        versionSupportsEclipseProjectIdentifier = supportsEclipseProjectIdentifier(targetGradleVersion);
    }

    @Override
    public void execute(SourceObjectMapping mapping) {
        Class<?> targetType = mapping.getTargetType();
        if (!versionSupportsEclipseProjectIdentifier) {
            if (EclipseProjectDependency.class.isAssignableFrom(targetType)) {
                mapping.mixIn(EclipseProjectDependencyTargetMixin.class);
            } else if (HierarchicalEclipseProject.class.isAssignableFrom(targetType)) {
                mapping.mixIn(EclipseProjectIdentifierMixin.class);
            }
        }
    }

    private boolean supportsEclipseProjectIdentifier(GradleVersion targetGradleVersion) {
        return targetGradleVersion.getBaseVersion().compareTo(GradleVersion.version("2.14")) >= 0;
    }
}
