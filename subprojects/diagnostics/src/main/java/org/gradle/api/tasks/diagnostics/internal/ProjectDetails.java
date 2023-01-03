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
import java.util.Objects;

public interface ProjectDetails {

    static ProjectDetails of(final Project project) {
        final String displayName = project.getDisplayName();
        final String description = project.getDescription();
        final String name = project.getName();
        final String path = project.getPath();
        return new ProjectDetails() {
            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public int hashCode() {
                return Objects.hash(displayName, name, path);
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof ProjectDetails)) {
                    return false;
                }
                ProjectDetails that = (ProjectDetails) obj;
                if (!this.getDisplayName().equals(that.getDisplayName())) {
                    return false;
                }
                if (!this.getName().equals(that.getName())) {
                    return false;
                }
                if (!this.getPath().equals(that.getPath())) {
                    return false;
                }
                return true;
            }
        };
    }

    String getDisplayName();

    @Nullable
    String getDescription();

    String getName();

    String getPath();
}
