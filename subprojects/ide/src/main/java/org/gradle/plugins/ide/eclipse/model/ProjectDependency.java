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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Preconditions;
import groovy.util.Node;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Objects;

/**
 * A classpath entry representing a project dependency.
 */
public class ProjectDependency extends AbstractClasspathEntry {

    private File publication;
    private TaskDependency buildDependencies;

    public ProjectDependency(Node node) {
        super(node);
        assertPathIsValid();
    }

    /**
     * Create a dependency on another Eclipse project.
     * @param path The path to the Eclipse project, which is the name of the eclipse project preceded by "/".
     */
    public ProjectDependency(String path) {
        super(path);
        assertPathIsValid();
    }

    public File getPublication() {
        return publication;
    }

    public void setPublication(File publication) {
        this.publication = publication;
    }

    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    public void setBuildDependencies(TaskDependency buildDependencies) {
        this.buildDependencies = buildDependencies;
    }

    private void assertPathIsValid() {
        Preconditions.checkArgument(path.startsWith("/"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ProjectDependency that = (ProjectDependency) o;
        return Objects.equals(publication, that.publication);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publication);
    }

    @Override
    public String getKind() {
        return "src";
    }

    @Override
    public String toString() {
        return "ProjectDependency{" +
            "publication=" + publication +
            ", path='" + path + '\'' +
            ", exported=" + exported +
            ", accessRules=" + accessRules +
            ", entryAttributes=" + entryAttributes +
            '}';
    }
}
