/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.InvalidUserDataException;

import java.io.File;
import java.io.Serializable;

public class BuildFileProjectSpec extends AbstractProjectSpec implements Serializable {
    private final File buildFile;

    public BuildFileProjectSpec(File buildFile) {
        this.buildFile = buildFile;
    }

    public String getDisplayName() {
        return String.format("project has build file '%s'", buildFile);
    }

    protected String formatNoMatchesMessage() {
        return String.format("No projects in this build have build file '%s'.", buildFile);
    }

    protected String formatMultipleMatchesMessage(Iterable<? extends ProjectIdentifier> matches) {
        return String.format("Multiple projects in this build have build file '%s': %s", buildFile, matches);
    }

    protected boolean select(ProjectIdentifier project) {
        return buildFile.equals(project.getBuildFile());
    }

    @Override
    protected void checkPreconditions(ProjectRegistry<?> registry) {
        if (!buildFile.exists()) {
            throw new InvalidUserDataException(String.format("Build file '%s' does not exist.", buildFile));
        }
        if (!buildFile.isFile()) {
            throw new InvalidUserDataException(String.format("Build file '%s' is not a file.", buildFile));
        }
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}