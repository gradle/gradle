/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.base.Strings;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectRegistry;

import java.io.Serializable;

public class ProjectPathProjectSpec extends AbstractProjectSpec implements Serializable {
    private final String projectPath;

    public ProjectPathProjectSpec(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getDisplayName() {
        return String.format("project has path '%s'", projectPath);
    }

    protected String formatNoMatchesMessage() {
        return String.format("No projects in this build have path '%s'.", projectPath);
    }

    protected String formatMultipleMatchesMessage(Iterable<? extends ProjectIdentifier> matches) {
        return String.format("Multiple projects in this build have path '%s': %s", projectPath, matches);
    }

    protected boolean select(ProjectIdentifier project) {
        return projectPath.equals(project.getPath());
    }

    @Override
    protected void checkPreconditions(ProjectRegistry<?> registry) {
        // TODO(radimk): pattern for path?
        if (Strings.isNullOrEmpty(projectPath)) {
            throw new InvalidUserDataException("Project path must not be empty.");
        }
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}