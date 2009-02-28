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

import java.io.File;

public class ProjectDirectoryProjectSpec implements ProjectSpec {
    private final File dir;

    public ProjectDirectoryProjectSpec(File dir) {
        this.dir = dir;
    }

    public String getDescription() {
        return String.format("with project directory '%s'", dir);
    }

    public boolean isSatisfiedBy(ProjectIdentifier element) {
        return dir.equals(element.getProjectDir());
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
