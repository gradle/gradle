/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.GradleInternal;
import org.gradle.tooling.internal.migration.DefaultArchive;
import org.gradle.tooling.internal.migration.DefaultProjectOutput;
import org.gradle.tooling.internal.protocol.InternalProjectOutput;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.gradle.tooling.model.migration.ProjectOutput;
import org.gradle.tooling.model.migration.TaskOutput;

import java.util.Collections;
import java.util.List;

public class MigrationModelBuilder implements BuildsModel {
    public boolean canBuild(Class<?> type) {
        return type == InternalProjectOutput.class;
    }

    public ProjectVersion3 buildAll(GradleInternal gradle) {
        Project root = gradle.getRootProject();
        List<TaskOutput> taskOutputs = Lists.newArrayList();
        Configuration configuration = root.getConfigurations().findByName("archives");
        if (configuration != null) {
            for (PublishArtifact artifact : configuration.getArtifacts()) {
                taskOutputs.add(new DefaultArchive(artifact.getFile()));
            }
        }
        return new DefaultProjectOutput(root.getName(), null,
                ImmutableDomainObjectSet.of(Collections.<ProjectOutput>emptyList()),
                ImmutableDomainObjectSet.of(taskOutputs));
    }
}
