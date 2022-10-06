/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.selection;

import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.execution.TaskSelection;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.io.File;

@ServiceScope(Scopes.BuildTree.class)
public interface BuildTaskSelector {
    Filter resolveExcludedTaskName(BuildState targetBuild, String taskName);

    /**
     * @param rootDir When not null, specifies the build to resolve tasks relative to. When null, resolve relative to the default build.
     * @param projectPath When not null, specifies the project within the target build to resolve tasks relative to. When null, resolve relative to the default project of the target build.
     */
    TaskSelection resolveTaskName(@Nullable File rootDir, @Nullable String projectPath, BuildState targetBuild, String taskName);

    BuildSpecificSelector relativeToBuild(BuildState target);

    /**
     * A selector that is contextualized to select tasks relative to some build.
     */
    interface BuildSpecificSelector {
        TaskSelection resolveTaskName(String taskName);
    }

    class Filter {
        private final BuildState build;
        private final Spec<Task> filter;

        public Filter(BuildState build, Spec<Task> filter) {
            this.build = build;
            this.filter = filter;
        }

        public BuildState getBuild() {
            return build;
        }

        public Spec<Task> getFilter() {
            return filter;
        }
    }
}
