/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.Project;

import javax.annotation.Nullable;

public interface ProjectDetails {

    static ProjectDetails of(final Project project) {
        return new ProjectDetails() {
            @Override
            public int hashCode() {
                return this.getPath().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof ProjectDetails)) {
                    return false;
                }
                ProjectDetails that = (ProjectDetails) obj;
                return this.isRootProject() == that.isRootProject() || this.getPath().equals(that.getPath());
            }

            @Override
            public boolean isRootProject() {
                return project == project.getRootProject();
            }

            @Override
            public String getPath() {
                return project.getPath();
            }

            @Override
            public String getDescription() {
                return project.getDescription();
            }
        };
    }

    boolean isRootProject();

    String getPath();

    @Nullable
    String getDescription();
}
