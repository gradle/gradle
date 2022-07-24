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
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;

import java.util.Objects;

/**
 * A classpath entry representing a project dependency.
 */
public class ProjectDependency extends AbstractClasspathEntry {

    private FileReference publication;
    private FileReference publicationSourcePath;
    private FileReference publicationJavadocPath;
    private DefaultTaskDependency buildDependencies = new DefaultTaskDependency();

    public ProjectDependency(Node node) {
        super(node);
        assertPathIsValid();
    }

    /**
     * Create a dependency on another Eclipse project.
     *
     * @param path The path to the Eclipse project, which is the name of the eclipse project preceded by "/".
     */
    public ProjectDependency(String path) {
        super(path);
        assertPathIsValid();
    }

    /**
     * Returns the file that can replace this ProjectDependency
     *
     * @since 5.6
     */
    public FileReference getPublication() {
        return publication;
    }

    /**
     * Sets the file that can replace this ProjectDependency
     *
     * @since 5.6
     */
    public void setPublication(FileReference publication) {
        this.publication = publication;
    }

    /**
     * Returns the source artifact of the project publication
     *
     * @see #getPublication()
     * @since 5.6
     */
    public FileReference getPublicationSourcePath() {
        return publicationSourcePath;
    }

    /**
     * Sets the source artifact of the project publication
     *
     * @see #getPublication()
     * @since 5.6
     */
    public void setPublicationSourcePath(FileReference publicationSourcePath) {
        this.publicationSourcePath = publicationSourcePath;
    }

    /**
     * Returns the javadoc artifact of the project publication
     *
     * @see #getPublication()
     * @since 5.6
     */
    public FileReference getPublicationJavadocPath() {
        return publicationJavadocPath;
    }

    /**
     * Sets the javadoc artifact of the project publication
     *
     * @see #getPublication()
     * @since 5.6
     */
    public void setPublicationJavadocPath(FileReference publicationJavadocPath) {
        this.publicationJavadocPath = publicationJavadocPath;
    }

    /**
     * Returns the tasks to be executed to build the file returned by {@link #getPublication()}
     * <p>
     * This property doesn't have a direct effect to the Gradle Eclipse plugin's behaviour. It is used, however, by
     * Buildship to execute the configured tasks each time before the user imports the project or before a project
     * synchronization starts in case this project is closed to build the substitute jar.
     *
     * @since 5.6
     */
    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    /**
     * Sets the tasks to be executed to build the file returned by {@link #getPublication()}
     *
     * @see #getBuildDependencies()
     * @since 5.6
     */
    public void buildDependencies(Object... buildDependencies) {
        this.buildDependencies.add(buildDependencies);
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
        return Objects.equals(publication, that.publication) &&
            Objects.equals(publicationSourcePath, that.publicationSourcePath) &&
            Objects.equals(publicationJavadocPath, that.publicationJavadocPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publication, publicationSourcePath, publicationJavadocPath);
    }

    @Override
    public String getKind() {
        return "src";
    }

    @Override
    public String toString() {
        return "ProjectDependency{" +
            "publication=" + publication +
            ", publicationSourcePath=" + publicationSourcePath +
            ", publicationJavadocPath=" + publicationJavadocPath +
            ", buildDependencies=" + buildDependencies +
            ", path='" + path + '\'' +
            ", exported=" + exported +
            ", accessRules=" + accessRules +
            ", entryAttributes=" + entryAttributes +
            '}';
    }
}
