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

import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectModel;

import java.io.Serializable;

public class FixedBuildIdentifierProvider implements Serializable {
    private final DefaultBuildIdentifier buildIdentifier;
    private final DefaultProjectIdentifier projectIdentifier;

    public FixedBuildIdentifierProvider(DefaultProjectIdentifier projectIdentifier) {
        this.buildIdentifier = projectIdentifier.getBuildIdentifier();
        this.projectIdentifier = projectIdentifier;
    }

    public DefaultBuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    public DefaultProjectIdentifier getProjectIdentifier() {
        return projectIdentifier;
    }

    public <T> ViewBuilder<T> applyTo(ViewBuilder<T> builder) {
        builder.mixInTo(BuildModel.class, this);
        builder.mixInTo(ProjectModel.class, this);
        return builder;
    }
}
