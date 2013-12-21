/*
 * Copyright 2009 the original author or authors.
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

public class ProjectDirectoryProjectSpec extends AbstractProjectSpec implements Serializable {
    private final File dir;

    public ProjectDirectoryProjectSpec(File dir) {
        this.dir = dir;
    }

    public String getDisplayName() {
        return String.format("with project directory '%s'", dir);
    }

    protected String formatNoMatchesMessage() {
        return String.format("No projects in this build have project directory '%s'.", dir);
    }

    protected String formatMultipleMatchesMessage(Iterable<? extends ProjectIdentifier> matches) {
        return String.format("Multiple projects in this build have project directory '%s': %s", dir, matches);
    }

    protected boolean select(ProjectIdentifier project) {
        return project.getProjectDir().equals(dir);
    }

    @Override
    protected void checkPreconditions(ProjectRegistry<?> registry) {
        if (!dir.exists()) {
            throw new InvalidUserDataException(String.format("Project directory '%s' does not exist.", dir));
        }
        if (!dir.isDirectory()) {
            throw new InvalidUserDataException(String.format("Project directory '%s' is not a directory.", dir));
        }
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
