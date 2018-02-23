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

package org.gradle.api.plugins.buildcomparison.gradle.internal;

import org.gradle.api.plugins.buildcomparison.gradle.GradleBuildInvocationSpec;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;
import org.gradle.util.GradleVersion;

import java.util.List;

public class ComparableGradleBuildExecuter {

    public static final GradleVersion PROJECT_OUTCOMES_MINIMUM_VERSION = GradleVersion.version("1.2");
    public static final GradleVersion EXEC_MINIMUM_VERSION = GradleVersion.version("1.2");

    private final GradleBuildInvocationSpec spec;

    public ComparableGradleBuildExecuter(GradleBuildInvocationSpec spec) {
        this.spec = spec;
    }

    public GradleBuildInvocationSpec getSpec() {
        return spec;
    }

    public boolean isExecutable() {
        return getGradleVersion().compareTo(EXEC_MINIMUM_VERSION) >= 0;
    }

    public GradleVersion getGradleVersion() {
        return GradleVersion.version(getSpec().getGradleVersion());
    }

    public ProjectOutcomes executeWith(ProjectConnection connection) {
        List<String> tasksList = getSpec().getTasks();
        String[] tasks = tasksList.toArray(new String[0]);
        List<String> argumentsList = getSpec().getArguments();
        String[] arguments = argumentsList.toArray(new String[0]);

        // Run the build and get the build outcomes model
        ModelBuilder<ProjectOutcomes> modelBuilder = connection.model(ProjectOutcomes.class);
        return modelBuilder.
            withArguments(arguments).
            forTasks(tasks).
            get();
    }
}
